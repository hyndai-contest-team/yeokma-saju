#!/usr/bin/env python3
"""Export the approved seal paths for app, web, desktop and print use.

python3 -m pip install -r tools/brand-requirements.txt
python3 tools/export_brand.py
Optional: --trace rebuilds the vector masters from sources/*-generated.png.
The default export preserves hand edits to the vector masters.
"""
from __future__ import annotations

import argparse
import copy
import io
import json
import re
import struct
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path

from PIL import Image, ImageDraw
import resvg_py

ROOT = Path(__file__).resolve().parents[1]
BRAND = ROOT / "assets/brand"
INK = "#A62F2B"
WHITE = "#FFFFFF"
SVG = "http://www.w3.org/2000/svg"
ANDROID = "http://schemas.android.com/apk/res/android"
ET.register_namespace("", SVG)
ET.register_namespace("android", ANDROID)


def write(path: Path, value: str | bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if isinstance(value, bytes):
        path.write_bytes(value)
    else:
        path.write_text(value, encoding="utf-8")


def svg_text(element: ET.Element) -> str:
    return ET.tostring(element, encoding="unicode")


def trace_master(source: str, destination: str, label: str) -> None:
    import vtracer

    image = Image.open(BRAND / "sources" / source).convert("RGBA")
    alpha = image.getchannel("A").point(lambda a: 255 if a >= 160 else 0)
    bbox = alpha.getbbox()
    if bbox is None:
        raise ValueError(f"Empty source: {source}")
    ink = alpha.crop(bbox)
    mask = Image.new("L", ink.size, 255)
    mask.paste(0, mask=ink)
    with tempfile.TemporaryDirectory() as temp:
        png, svg = Path(temp) / "mask.png", Path(temp) / "trace.svg"
        mask.save(png)
        vtracer.convert_image_to_svg_py(
            str(png), str(svg), colormode="binary", mode="spline",
            filter_speckle=20, corner_threshold=75, length_threshold=4,
            max_iterations=10, splice_threshold=45, path_precision=2,
        )
        paths = ET.parse(svg).getroot()
    width, height = ink.size
    scale = 960 / max(width, height)
    tx, ty = (1024 - width * scale) / 2, (1024 - height * scale) / 2
    doc = ET.Element(f"{{{SVG}}}svg", {"viewBox": "0 0 1024 1024", "role": "img", "aria-labelledby": "title"})
    ET.SubElement(doc, f"{{{SVG}}}title", {"id": "title"}).text = label
    group = ET.SubElement(doc, f"{{{SVG}}}g", {"transform": f"translate({tx:.4f} {ty:.4f}) scale({scale:.6f})", "fill": INK})
    for path in paths:
        if path.tag.endswith("path"):
            path.attrib.pop("fill", None)
            group.append(path)
    ET.indent(doc)
    write(BRAND / "svg" / destination, '<?xml version="1.0" encoding="utf-8"?>\n' + svg_text(doc) + '\n')


def composition(master: str, size: int = 1024, inset: float = 0, background: str | None = None, color: str | None = None) -> str:
    source = ET.parse(BRAND / "svg" / master).getroot()
    doc = ET.Element(f"{{{SVG}}}svg", {"viewBox": f"0 0 {size} {size}", "width": str(size), "height": str(size), "role": "img", "aria-labelledby": "title"})
    title = next(child for child in source if child.tag.endswith("title"))
    doc.append(copy.deepcopy(title))
    if background:
        ET.SubElement(doc, f"{{{SVG}}}rect", {"width": str(size), "height": str(size), "fill": background})
    scale = (size - 2 * inset) / 1024
    group = ET.SubElement(doc, f"{{{SVG}}}g", {"transform": f"translate({inset} {inset}) scale({scale})"})
    for child in source:
        if child.tag.endswith("title"):
            continue
        item = copy.deepcopy(child)
        if color:
            for node in item.iter():
                if "fill" in node.attrib:
                    node.set("fill", color)
        group.append(item)
    return svg_text(doc)


def raster(svg: str, size: int, opaque: bool = False) -> Image.Image:
    image = Image.open(io.BytesIO(resvg_py.svg_to_bytes(svg_string=svg, width=size, height=size))).convert("RGBA")
    if opaque:
        if image.getchannel("A").getextrema() != (255, 255):
            raise ValueError("Opaque export has transparent pixels")
        return image.convert("RGB")
    return image


def png(path: Path, svg: str, size: int, opaque: bool = False) -> Image.Image:
    image = raster(svg, size, opaque)
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, optimize=True)
    return image


def ico(path: Path, images: list[Image.Image]) -> None:
    # Keep each size's chosen artwork instead of rescaling the largest frame.
    payloads = []
    for image in images:
        buffer = io.BytesIO()
        image.save(buffer, format="PNG")
        payloads.append(buffer.getvalue())
    offset = 6 + 16 * len(images)
    header = struct.pack("<HHH", 0, 1, len(images))
    entries = []
    for image, payload in zip(images, payloads):
        size = image.width
        entries.append(struct.pack("<BBBBHHII", 0 if size == 256 else size, 0 if size == 256 else size, 0, 0, 1, 32, len(payload), offset))
        offset += len(payload)
    write(path, header + b"".join(entries) + b"".join(payloads))


def android_foreground(master: str) -> str:
    source = ET.parse(BRAND / "svg" / master).getroot()
    vector = ET.Element("vector", {f"{{{ANDROID}}}width": "108dp", f"{{{ANDROID}}}height": "108dp", f"{{{ANDROID}}}viewportWidth": "108", f"{{{ANDROID}}}viewportHeight": "108"})
    # Ink fits within the central 48 dp, leaving room for circular OEM masks.
    parent = ET.SubElement(vector, "group", {f"{{{ANDROID}}}translateX": "28.4", f"{{{ANDROID}}}translateY": "28.4", f"{{{ANDROID}}}scaleX": "0.05", f"{{{ANDROID}}}scaleY": "0.05"})

    def convert(node: ET.Element, parent: ET.Element, fill: str) -> None:
        if node.tag.endswith("title"):
            return
        fill = node.get("fill", fill)
        transform = node.get("transform", "")
        supported = r"(translate|scale)\(([^)]*)\)"
        if re.sub(supported, "", transform).strip():
            raise ValueError("Flatten unsupported SVG transforms before Android export: " + transform)
        for name, values in re.findall(supported, transform):
            parts = [float(x) for x in re.split(r"[ ,]+", values.strip())]
            if name == "translate":
                attrs = {f"{{{ANDROID}}}translateX": str(parts[0]), f"{{{ANDROID}}}translateY": str(parts[1] if len(parts) > 1 else 0)}
            else:
                attrs = {f"{{{ANDROID}}}scaleX": str(parts[0]), f"{{{ANDROID}}}scaleY": str(parts[1] if len(parts) > 1 else parts[0])}
            parent = ET.SubElement(parent, "group", attrs)
        if node.tag.endswith("path"):
            ET.SubElement(parent, "path", {f"{{{ANDROID}}}fillColor": fill, f"{{{ANDROID}}}pathData": node.attrib["d"]})
        for child in node:
            convert(child, parent, fill)

    for child in source:
        convert(child, parent, INK)
    ET.indent(vector)
    return '<?xml version="1.0" encoding="utf-8"?>\n' + svg_text(vector) + '\n'


def export() -> None:
    full, micro = "yeokma-seal-mark.svg", "yeokma-monogram-mark.svg"
    mark = composition(full)
    on_white = composition(full, background=WHITE)
    app = composition(full, inset=128, background=WHITE)
    maskable = composition(full, inset=216, background=WHITE)
    small = composition(micro, inset=24, background=WHITE)
    write(BRAND / "svg/yeokma-seal.svg", on_white)
    write(BRAND / "svg/yeokma-seal-monochrome.svg", composition(full, color="#000000"))
    write(BRAND / "svg/yeokma-seal-reverse.svg", composition(full, color=WHITE))
    write(BRAND / "svg/yeokma-app-icon.svg", app)
    for size in (512, 1024, 2048):
        png(BRAND / f"png/yeokma-seal-{size}.png", on_white, size, True)
        png(BRAND / f"png/yeokma-seal-transparent-{size}.png", mark, size)
    png(BRAND / "png/yeokma-seal-reverse-1024.png", composition(full, color=WHITE), 1024)
    for size in (512, 1024):
        png(BRAND / f"app/app-icon-{size}.png", app, size, True)
    # Favicons use the responsive 馬 seal; the complete name stays on app icons.
    write(BRAND / "web/favicon.svg", small)
    favicons = [png(BRAND / f"web/favicon-{size}x{size}.png", small, size, True) for size in (16, 32, 48)]
    ico(BRAND / "web/favicon.ico", favicons)
    png(BRAND / "web/apple-touch-icon.png", app, 180, True)
    for size in (192, 512):
        png(BRAND / f"web/icon-{size}.png", app, size, True)
        png(BRAND / f"web/icon-maskable-{size}.png", maskable, size, True)
    manifest = {"name": "역마사주", "short_name": "역마사주", "lang": "ko", "display": "standalone", "background_color": WHITE, "theme_color": WHITE, "icons": []}
    for size in (192, 512):
        for suffix, purpose in (("", "any"), ("-maskable", "maskable")):
            manifest["icons"].append({"src": f"icon{suffix}-{size}.png", "sizes": f"{size}x{size}", "type": "image/png", "purpose": purpose})
    write(BRAND / "web/site.webmanifest", json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
    write(BRAND / "web/head.html", '''<!-- Copy web/ to your site's /brand/ directory. -->
<link rel="icon" href="/brand/favicon.ico" sizes="16x16 32x32 48x48">
<link rel="icon" href="/brand/favicon.svg" type="image/svg+xml" sizes="any">
<link rel="apple-touch-icon" href="/brand/apple-touch-icon.png" sizes="180x180">
<link rel="manifest" href="/brand/site.webmanifest">
<meta name="theme-color" content="#FFFFFF">
''')
    desktop = [raster(small if size <= 32 else app, size, True) for size in (16, 32, 48, 64, 128, 256)]
    ico(BRAND / "desktop/app-icon.ico", desktop)
    icon = raster(app, 1024, True)
    (BRAND / "desktop").mkdir(exist_ok=True)
    icon.save(BRAND / "desktop/app-icon.icns", format="ICNS",
              append_images=[raster(small, size, True) for size in (32, 64)])
    # Standalone iOS AppIcon asset catalog, with one universal 1024px image.
    png(BRAND / "ios/AppIcon.appiconset/app-icon-1024.png", app, 1024, True)
    contents = {"images": [{"filename": "app-icon-1024.png", "idiom": "universal", "platform": "ios", "size": "1024x1024"}], "info": {"author": "xcode", "version": 1}}
    write(BRAND / "ios/AppIcon.appiconset/Contents.json", json.dumps(contents, indent=2) + '\n')
    adaptive = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/brand_icon_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
'''
    xml_files = {
        "drawable/ic_launcher_foreground.xml": android_foreground(full),
        "values/brand_icon_colors.xml": '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="brand_icon_background">#FFFFFF</color>\n</resources>\n',
        "mipmap-anydpi-v26/ic_launcher.xml": adaptive,
        "mipmap-anydpi-v26/ic_launcher_round.xml": adaptive,
    }
    for target in (BRAND / "android/res", ROOT / "app/app/src/main/res"):
        for name, content in xml_files.items():
            write(target / name, content)
        for density, size in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
            png(target / f"mipmap-{density}/ic_launcher.png", app, size, True)
            png(target / f"mipmap-{density}/ic_launcher_round.png", maskable, size, True)
    preview(app, mark, small, maskable)


def preview(app: str, mark: str, small: str, maskable: str) -> None:
    sheet = Image.new("RGB", (1400, 820), WHITE)
    draw = ImageDraw.Draw(sheet)
    draw.text((48, 30), "YEOKMA SAJU  /  SEAL IDENTITY  /  WHITE BACKGROUND", fill="#333333")
    for x, svg, label in ((48, app, "APP / FULL NAME"), (520, mark, "TRANSPARENT MARK"), (992, small, "FAVICON / MA")):
        size = 360
        image = raster(svg, size)
        sheet.paste(image, (x, 90), image)
        draw.text((x, 475), label, fill="#333333")
    for i, size in enumerate((16, 32, 48, 64)):
        image = raster(small, size, True)
        x = 48 + i * 110
        sheet.paste(image, (x, 560))
        draw.text((x, 640), f"{size}px", fill="#444444")
    # Circular preview of the maskable icon, on a neutral presentation field.
    tile = raster(maskable, 192, True)
    circle = Image.new("L", (192, 192), 0)
    ImageDraw.Draw(circle).ellipse((0, 0, 191, 191), fill=255)
    sheet.paste(tile, (620, 550), circle)
    draw.text((590, 770), "MASKABLE / CIRCLE SAFE", fill="#444444")
    dark = Image.new("RGB", (300, 230), "#171717")
    reverse = raster(composition("yeokma-seal-mark.svg", color=WHITE), 200)
    dark.paste(reverse, (50, 15), reverse)
    sheet.paste(dark, (992, 540))
    draw.text((992, 785), "REVERSE MARK / OPTIONAL", fill="#444444")
    sheet.save(BRAND / "preview.png", optimize=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--trace", action="store_true", help="Recreate SVG masters from the generated references")
    args = parser.parse_args()
    if args.trace:
        trace_master("seal-generated.png", "yeokma-seal-mark.svg", "역마사주 — 驛馬四柱 사각 도장")
        trace_master("monogram-generated.png", "yeokma-monogram-mark.svg", "역마사주 — 馬 작은 도장")
    export()
    print(f"Exported brand assets to {BRAND}")

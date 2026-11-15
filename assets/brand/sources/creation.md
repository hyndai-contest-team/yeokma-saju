# 제작 기록

2026-11-15. 내장 이미지 생성 도구(built-in image generation)로 도장 형태를 제작하고, 경로 기반 SVG로 벡터화한 뒤 플랫폼별 파일로 내보냈습니다. 외부 폰트나 기존 로고를 사용하지 않았습니다. 원본 PNG의 질감·미세 반투명 픽셀은 벡터화 때 정리했고 최종 인주 색은 `#A62F2B`로 통일했습니다.

사용자가 추가로 지정한 **흰 배경 `#FFFFFF`**는 최종 기본 SVG·앱·파비콘·스토어 파일에 적용했습니다. 아래 프롬프트의 투명 배경은 독립된 도장 경로를 얻기 위한 제작 단계이며 최종 기본 배경 지정과 다릅니다.

## 기본 도장 프롬프트

```text
Use case: logo-brand. Create one finished primary logo asset, a square Korean name seal for the app named exactly 驛馬四柱. Flat two-dimensional vector-like cinnabar red ink, on genuine transparent background. It must be a single ink color #A62F2B (no gradients). Four legible traditional Chinese characters in a tightly composed two-by-two grid, modern reading order: TOP LEFT 驛, TOP RIGHT 馬, BOTTOM LEFT 四, BOTTOM RIGHT 柱. These exact characters only, do not use simplified 驿, do not substitute or invent strokes. Draw authentic, bold, squared seal-carving-inspired lettering with clear counters and restrained organic hand-carved edges; preserve correct recognizable character structure. Enclose the four characters in one thick, nearly square red seal border with very subtly rounded hand-cut corners. The letters themselves and border are red ink, the spaces between strokes and inside counters are transparent. Compact harmonious spacing: all four characters same optical square area, slightly generous channels between them. The complete square seal occupies about 84% of a 1024 by 1024 square canvas, perfectly centered with equal transparent margins. Professional timeless mystical Korean fortune-telling app identity. The finished mark should have a strong clear silhouette at small icon sizes. Flat front-on artwork only, no mockup, no paper, no lighting, no shadow, no three-dimensional stamp object, no decorative horse illustration, no tiny distress speckles, no caption, no English text, no watermark. Deliver only the isolated square seal logo.
```

## 작은 도장 프롬프트

참조 이미지: 바로 위 프롬프트로 만든 기본 도장. 입력 파일은 `seal-generated.png`입니다.

```text
Use case: logo-brand. Create the tiny-size companion icon for this exact square red seal logo. Keep the same nearly square thick border, cinnabar red ink, bold hand-carved visual identity and transparent background. Change only the interior from four characters to ONE enlarged, correct traditional Chinese character: 馬. It is the character currently in the top-right of the supplied logo. Remove the other three characters and enlarge/recenter 馬 to fill the inside of the square. Keep the four dot strokes at the bottom of 馬 clearly separated and the three horizontal interior spaces clearly open. Preserve enough negative space for recognition at 16–32 pixels; reduce irregular texture and tiny distress entirely. This is the approved logo's responsive favicon companion, not a different brand. One flat color #A62F2B, no gradients, no shadows, no mockup, no other text. Deliver one square image, centered stamp taking about 84% of the canvas, true transparent counters and outer background.
```

## 내보내기

- `tools/export_brand.py --trace`: 알파 160 이상인 영역을 단색 경로로 변환하고 작은 얼룩을 제거합니다. 원본 벡터 좌표계는 1024×1024이며 가장 긴 인주 변은 960입니다.
- `tools/export_brand.py`: SVG 원본을 보존하며 플랫폼용 파일을 다시 만듭니다. 폰트·비트맵을 SVG 안에 넣지 않습니다.
- 기본/앱/웹 자산 배경: `#FFFFFF`. 별도의 투명·검정·흰 인주 보조 파일도 제공합니다.
- Android 전경과 SVG는 같은 경로를 사용합니다. Android 리소스의 복사본도 `android/res/`에 함께 제공합니다.

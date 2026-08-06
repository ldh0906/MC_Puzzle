# MCPuzzle resource pack

Paper 1.20.1용 미궁 GUI 전용 리소스팩입니다. 일반 월드 블록은 바꾸지 않으며 `PAPER`의 CustomModelData 12001~12016만 사용합니다.

## 빌드

PowerShell에서 `./build.ps1`을 실행하면 `build/MCPuzzle-1.0.0.zip`과 SHA-1 값이 생성됩니다. ZIP 최상위에는 반드시 `pack.mcmeta`, `pack.png`, `assets/`가 있어야 합니다.

아이콘 원본을 다시 처리할 때는 먼저 이미지 생성 스킬의 `remove_chroma_key.py`로 배경을 투명화한 뒤 `tools/slice_atlas.py`를 실행합니다.

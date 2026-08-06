# MCPuzzle Map Packs

미궁마다 별도 디렉터리를 사용합니다. 맵 팩은 주석을 허용하는 JSONC로 작성하고,
`schema/map-pack.schema.json` 검증을 통과해야 활성화할 수 있습니다.

권장 구조:

```text
map-packs/
  schema/map-pack.schema.json
  <maze-id>/
    map.jsonc
```

공식 `a-to-z-archive-20` 팩은 `GENERATED_VOID` 방식으로, 플러그인이 상대 정수 좌표와 `world.generator`
팔레트를 이용해 인스턴스 월드를 생성합니다. 따라서 별도의 바이너리 월드 템플릿은
필요하지 않습니다. `buildBounds`, `playBounds`, 스폰, 체크포인트, 기믹 좌표는 모두
각 인스턴스 월드 원점을 기준으로 합니다.

공식 팩은 `generate_map.py`로 재생성하고 `validate_pack.py`로 결정성, 20개 방, 1–4인,
방 경계, 정답 노출, 서로 다른 바닥 도식, 발판·버튼·레버 미사용을 검사합니다.

`mcpuzzle-map-tool`의 Importer는 과거 SCX 분석 자료를 조사하기 위한 보조 도구이며
공식 활성 팩을 만들거나 덮어쓰지 않습니다.

# MCPuzzle Map Tool

`analyze_scx.py`가 생성한 분석 JSON을 사람이 편집할 수 있는 JSONC 맵 팩 초안으로
변환하는 독립 실행 모듈입니다. 과거 50 Rooms 조사 자료의 원본 1·3·8·19·42
스테이지만 고정된 순서로 변환하며, 원본 방/Location 좌표와 호송 경로 메타데이터를
보존합니다. 이 결과는 분석용 초안이며 공식 3개 미궁·30방 컬렉션으로 로드되지 않습니다.

```powershell
.\gradlew.bat :mcpuzzle-map-tool:run --args="analysis_50rooms/map_data.json analysis-drafts/legacy-50rooms.jsonc"
```

출력은 항상 동일한 입력에 대해 동일한 UTF-8 JSON으로 생성됩니다. 기존 파일이
있으면 `map.jsonc.bak-<UTC 타임스탬프>`로 복사한 후, 같은 디렉터리의 임시
파일을 원자적으로 교체합니다. 원자적 교체를 지원하지 않는 파일 시스템에서는
오류로 종료하여 부분 기록을 남기지 않습니다.

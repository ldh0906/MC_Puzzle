# MCPuzzle — 심야의 난이도 미궁

Paper 1.20.1 / Java 17용 1–4인 파티 미궁 플러그인입니다. `/maze`에서 난이도를 고르면 파티 전용 void 차원이 만들어지고 해당 미궁의 방을 순서대로 풉니다.

공식 콘텐츠는 쉬움 12방, 보통 12방, 어려움 5방으로 분리된 총 29방입니다. 방 이름은 알파벳 대신 고유 제목을 사용하며, 방 안의 발판·버튼·전시물을 정답 입력과 단서 탐색에 사용합니다.

## 현재 구현

- 1–4인 파티 생성, 초대, 수락/거절, 추방, 해산
- 파티별 `mcpuzzle_<UUID>` 전용 void 월드와 선택한 난이도 미궁 자동 생성
- 방마다 서로 다른 블록 바닥 도식과 자동 지급되는 증거 기록서
- `/maze answer <정답>` 텍스트 입력, 유니코드/대소문자 정규화
- 오답 뒤 10–18초 파티 공용 입력 잠금으로 무작위 대입 억제
- 미궁별 방 단위 체크포인트와 3개 세이브 슬롯, 정확한 원래 명단만 재개
- 세이브 소유자·저장 당시 파티장·OP 권한 정책과 7일 만료
- 참가자 이탈/AFK/리소스팩 실패 시 파티 전체 중단
- 3단계 힌트, 파티 크기별 순위표, 플레이어 상태 복구
- 로비와 인스턴스 사이 포털·텔레포트·가시성·아이템·피해·명령 격리
- 퀴즈맵 전용 리소스팩의 로컬 호스팅과 해시 검증

방별 해법 구조와 공정성 기준은 [29개 방 설계표](map-packs/difficulty-mazes-30/ROOM_DESIGN.md)에 정리되어 있습니다.

## 빌드와 검증

```powershell
py -3 map-packs/difficulty-mazes-30/generate_map.py
py -3 map-packs/difficulty-mazes-30/validate_pack.py
.\gradlew.bat clean test build
pwsh -NoProfile -ExecutionPolicy Bypass -File .\server\verify-startup.ps1
```

배포 JAR은 `mcpuzzle-paper/build/libs/mcpuzzle-paper-0.1.0-SNAPSHOT.jar`입니다. 시작 검증기는 JAR을 테스트 서버에 복사하고 Paper를 실제로 시작한 뒤 다음 항목을 확인합니다.

- 플러그인이 쉬움 12·보통 12·어려움 5, 총 29개 방을 로드해 READY가 되는가
- 서버 데이터 폴더에 최신 공식 맵이 배포되는가
- 세 임시 인스턴스에 29개 방·바닥 도식·입체 구조물·선언된 발판/버튼·표지판을 실제 생성하는가
- 검증 인스턴스를 언로드하고 표시된 임시 월드 폴더를 삭제하는가
- 리소스팩 URL이 응답하고 다운로드 SHA-1이 빌드 결과와 같은가
- Paper가 `stop` 명령으로 오류 없이 종료되는가

## 설치와 실행

저장소에 포함된 서버를 사용할 때:

```powershell
.\start.bat
```

기본 메모리는 2G, 최대 메모리는 4G입니다. 별도 Paper 서버에 설치하려면 빌드 JAR을 `plugins/`에 복사하세요. 첫 시작 시 `plugins/MCPuzzle/config.yml`, 공식 맵, 리소스팩이 만들어집니다. 공식 활성 맵은 플러그인 버전에 종속되므로 업그레이드 시 번들 버전으로 갱신됩니다.

로컬 테스트 서버와 리소스팩 설정은 [서버 안내](server/README.md)를 참고하세요.

## 주요 명령어

| 명령 | 설명 |
| --- | --- |
| `/maze` | 메인 GUI |
| `/maze party create` | 1인 파티 생성 |
| `/maze party invite <player>` | 파티 초대 |
| `/maze accept\|deny <leader>` | 초대 수락/거절 |
| `/maze party kick <player>` | 파티원 추방 |
| `/maze party status\|leave\|disband` | 파티 상태/탈퇴/해산 |
| `/maze start <easy\|normal\|hard> [1-3]` | 난이도와 슬롯을 골라 처음부터 시작 |
| `/maze queue cancel` | 입장 대기 취소(파티장) |
| `/maze saves` | 볼 수 있는 현재 미궁 세이브 목록 |
| `/maze resume <easy\|normal\|hard> <1-3> [현재소유자]` | 저장 당시 전원이 함께 있을 때 재개 |
| `/maze leave` | 체크포인트 저장 후 파티 전체 퇴장(파티장) |
| `/maze delete <1-3> [현재소유자]` | 권한이 있는 세이브 삭제 |
| `/maze hint request\|confirm\|decline\|view <1-3>` | 단계형 힌트 |
| `/maze answer <정답>` | 현재 방의 텍스트 정답 제출 |
| `/maze leaderboard [easy\|normal\|hard] [1-4]` | 미궁·파티 크기별 순위표 |
| `/maze status` | 플러그인/세션 상태 |
| `/maze admin reload` | 설정과 공식 맵 검증 |
| `/maze admin saves <owner>` | 다른 소유자의 슬롯 열람(OP) |
| `/maze admin delete <owner> <slot>` | 다른 소유자의 슬롯 삭제(OP) |

## 저장 규칙

- 방 1을 완료하기 전에는 미궁을 최초부터 다시 시작해야 합니다.
- 방을 하나 이상 완료하면 다음 방 체크포인트가 슬롯에 저장됩니다.
- 저장 당시 참가했던 플레이어 전원이 그대로 온라인이어야 재개할 수 있습니다.
- 슬롯은 현재 소유자, 저장 당시 파티장 또는 OP만 볼 수 있고 삭제할 수 있습니다.
- 비정상 종료로 끊긴 ACTIVE/PROVISIONING/QUEUED 진행은 폐기하고, 이미 SUSPENDED로 확정된 슬롯만 유지합니다.

## 구성

활성 팩: `plugins/MCPuzzle/map-packs/difficulty-mazes-30/{easy,normal,hard}.jsonc`

```yaml
instances:
  max-active-worlds: 2
  afk-timeout-minutes: 10
containment:
  operator-bypass: false
  allowed-commands: [maze, 미궁]
lobby:
  world: world
resource-pack:
  required: true
  url: 'http://127.0.0.1:8123/MCPuzzle-1.0.0.zip'
  sha1: 'resource-pack/build/MCPuzzle-1.0.0.sha1의 값'
```

## 모듈

- `mcpuzzle-core`: Bukkit 비의존 세션·파티·저장·힌트·정답 상태 머신
- `mcpuzzle-paper`: Paper 연동, 생성 월드, SQLite, 격리, GUI/명령
- `map-packs/difficulty-mazes-30`: 공식 3개 미궁·29방 생성기, 검증기, 설계표(기존 경로명 유지)
- `mcpuzzle-map-tool`: 과거 SCX 분석 JSON을 조사하기 위한 비활성 보조 변환기

# MCPuzzle 로컬 Paper 서버

이 폴더에는 Paper 1.20.1 build 196과 Zertan 로비가 준비되어 있습니다.

## 실행

저장소 루트의 `start.bat`을 더블클릭하면 `2G~4G` 메모리로 실행됩니다. 첫 실행에는
Mojang EULA 동의 여부를 `Y/N`으로 직접 묻고, 동의한 경우에만 `eula.txt`를 생성합니다.

PowerShell에서 직접 실행하려면 먼저 플러그인을 빌드한 뒤:

```powershell
.\server\start.ps1 -AcceptEula -MinMemory 2G -MaxMemory 4G
```

`-AcceptEula`는 Mojang EULA에 동의하는 경우에만 사용하세요. 스크립트는 빌드 JAR을
`server/plugins/MCPuzzle.jar`로 복사하고 Java 17 이상을 확인한 다음 Paper를 실행합니다.
메모리는 `-MinMemory 1G -MaxMemory 2G`처럼 바꿀 수 있습니다.

두 번째 실행부터는 EULA 파일이 있으므로 다음처럼 실행할 수 있습니다.

```powershell
.\server\start.ps1
```

직접 실행할 때의 기본 메모리도 최소 `2G`, 최대 `4G`입니다.

첫 부팅 뒤 `server/plugins/MCPuzzle/config.yml`의 필수 리소스 팩 URL/SHA-1을 설정하세요.
외부 팩이 아직 없다면 플러그인은 `DEGRADED`로 부팅되고 미궁 입장만 차단됩니다.

## Zertan 로비

- 기본 월드: `world/`, `server.properties`의 `level-name=world`
- 안전 스폰: `(-43, 97, 0)`
- 월드보더: 중심 `(16, 48)`, 크기 448
- 원본: [CurseForge project 874445 / file 4623072](https://www.curseforge.com/minecraft/worlds/zertan)
- 원본 압축 SHA-256: `5E0FBC8E23BC48F2644A05001589186E4C6387EC8F2547CCC147848500E1B530`
- 라이선스 문서: `world/Licence.txt`

공개 배포 전에 CurseForge의 최신 라이선스 표기와 포함된 `Licence.txt`를 다시
확인하세요. 공개 파일은 일부 외곽 청크가 빠진 데모입니다.

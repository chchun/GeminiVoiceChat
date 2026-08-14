# 002 — 구현 계획 (plan)

## 설계 결정

### D1. 액션 전송도 `AiRepository` 경계를 통과한다

| 대안 | 평가 |
|---|---|
| **(A) `AiRepository.sendA2UIAction(...)` 메서드 추가** ✅ | UI 계층이 OkHttp/URL 을 모른다 (P1/P4 유지). Mock 대체 가능 (P6) |
| (B) 별도 `A2UIActionClient` 신설 + DI | 인터페이스·배선 1쌍 추가 비용 대비 이득 없음 — 액션도 "AI 서버와의 대화"의 일부 |
| (C) ViewModel 에서 직접 OkHttp | P1 위반. 탈락 |

반환은 도메인 모델 `A2UIActionResult(ok, markdown)` — raw JSON 비노출 (P4).

### D2. 액션 URL = `SERVER_URL` 에서 유도

`SERVER_URL` 은 `http://host:8000/chat/stream` 형태다. OkHttp `HttpUrl.resolve("/a2ui/action")` 로
같은 호스트의 절대 경로를 유도한다. 버린 대안: BuildConfig 필드 추가 — 설정 2곳 동기화
부담, 서버가 같은 호스트라는 사실이 이미 계약.

### D3. 제출 성공 = 버튼 치환 (서피스 불변 갱신)

성공 시 해당 서피스의 components 에서 **같은 eventName 을 가진 ButtonComp** 를 찾아
`TextComp("✅ 등록이 완료되었습니다", variant=caption)` 로 치환한다.
- 버튼 id("submit")를 하드코딩하지 않고 eventName 으로 찾는 이유: id 는 서버 구현 상세,
  eventName 은 계약이다.
- 폼 필드는 유지 → 입력 내역이 대화 기록으로 남는다 (사용자 위임 결정, spec 참조).
- 실패 시 치환하지 않는다 → 버튼 활성 유지 = 재시도 경로.

### D4. VM 흐름 = 기존 스트리밍 placeholder 재사용

`register_safety_report` 분기에서 AI placeholder(isStreaming=true) 를 추가하고
`viewModelScope.launch` 로 왕복 → 응답 md 를 그 메시지에 채우고 finalize.
버린 대안: 별도 로딩 다이얼로그 — 채팅 UX 와 이질적, 기존 패턴 재사용이 낫다.

### D5. 파일 첨부 (T105) — 선택은 렌더러, 바이트는 UI 계층에서 읽는다

- 사진 선택 = **Photo Picker** (`PickVisualMedia` / `PickMultipleVisualMedia`) — 런타임
  권한 불필요 (READ_MEDIA_IMAGES 접근 방식 탈락 사유: 권한 UX 비용).
- 바이트 읽기는 **선택 직후 Composable 스코프(IO 디스패처)** 에서 수행하고, 결과를
  `A2UIPickedFile(name, mimeType, sizeBytes, bytes)` 로 dataModel(`/report/photos`)에 저장.
  버린 대안: URI 만 저장하고 제출 시 ViewModel 이 읽기 — VM 에 ContentResolver(플랫폼)
  의존이 생겨 P1 정신 위반, 추상 인터페이스 신설은 과설계.
- 제출 시 VM 이 context 에서 `A2UIPickedFile` 목록을 분리 — action JSON 에는 **파일명만**
  남기고(bytes 직렬화 방지), bytes 는 도메인 모델 `A2UIFile` 로 리포지토리에 전달.
- 메모리: bytes 를 힙에 들고 있는 트레이드오프 수용 — `maxSizeMb`(서버 50MB)·`maxFiles`(5)
  제한 검사로 상한. 초과 파일은 스킵 + 안내 문구.

## 영향 범위

| 파일 | 변경 |
|---|---|
| `domain/model/A2UIActionResult.kt` | **신규** (D1) |
| `domain/repository/AiRepository.kt` | 수정 — `sendA2UIAction` 추가 (D1) |
| `data/repository/RemoteAiRepository.kt` | 수정 — JSON POST + 응답 파싱 (D2) |
| `data/repository/MockAiRepository.kt` | 수정 — 성공 모사 (P6) |
| `ui/chat/ChatViewModel.kt` | 수정 — register_safety_report 서버 왕복 + 버튼 치환 (D3/D4) |
| `a2ui/*` | **0줄** |

## 검증 전략

- 빌드/테스트: `gradlew test` + `assembleDebug` (기존 테스트 무수정 green — P10)
- curl 로 `/a2ui/action` 단독 왕복 확인 (엔드포인트 실존·응답 형태)
- 실기기: 수용 1~3 `[needs-device]`

## 리스크

- **R1.** `/a2ui/action` 이 배포 서버에 아직 안 올라갔을 수 있음 — curl 로 선확인, 404 면
  서버 배포 대기 (클라이언트 코드는 그대로 유효)
- **R2.** context 의 `photos` 가 빈 리스트로 넘어감 — 서버 `post_opinion` 이 빈 리스트를
  허용함을 코드로 확인했으나 실등록은 실기기 검증 몫 (P9)
- **R3.** 등록은 실제 백엔드(세이플린 dev)에 데이터를 만든다 — 테스트 시 더미 데이터가
  등록됨을 사용자에게 고지

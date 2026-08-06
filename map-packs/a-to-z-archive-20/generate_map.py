from __future__ import annotations

import json
import math
from pathlib import Path


MATERIALS = {
    ".": "BLACK_CONCRETE",
    "#": "DEEPSLATE_TILES",
    "C": "CYAN_CONCRETE",
    "G": "GOLD_BLOCK",
    "P": "PURPUR_BLOCK",
    "R": "RED_CONCRETE",
    "W": "WHITE_CONCRETE",
    "B": "BLUE_CONCRETE",
    "L": "LIME_CONCRETE",
    "O": "ORANGE_CONCRETE",
    "M": "MAGENTA_CONCRETE",
    "Y": "YELLOW_CONCRETE",
}


def puzzle(letter: str, title: str, question: str, pages: list[str], answer_format: str,
           answers: list[str], difficulty: int, explanation: str, wrong: list[str], hints: list[str]) -> dict:
    assert len(pages) <= 8 and all(0 < len(page) <= 240 for page in pages)
    assert len(hints) == 3
    return {
        "letter": letter,
        "title": title,
        "question": question,
        "pages": pages,
        "answerFormat": answer_format,
        "answers": answers,
        "difficulty": difficulty,
        "explanation": explanation,
        "wrong": wrong,
        "hints": hints,
    }


PUZZLES = [
    puzzle("A", "철자 보존 법칙", "두 영어 문장은 공백을 빼면 알파벳의 종류와 개수가 완전히 같습니다. 빈칸 일곱 글자를 복원하세요.", [
        "원문\nWILLIAM SHAKESPEARE\n\n변환문\nI AM A WEAKISH _______",
        "대소문자와 공백은 무시합니다. 원문에서 이미 사용한 글자를 하나씩 지우면 남는 글자는 E, E, L, L, P, R, S입니다.",
        "남은 일곱 글자를 모두 한 번씩 사용해 '철자를 쓰는 사람'과 관계있는 영어 단어를 만드세요.",
    ], "영어 7글자", ["speller"], 1,
       "WILLIAMSHAKESPEARE와 IAMAWEAKISHSPELLER는 같은 문자 빈도를 가진다. 남은 문자를 재배열하면 SPELLER다.",
       ["speaker", "spellar", "letters"],
       ["문자 빈도를 세어 보세요.", "남은 글자는 EEllPRS입니다.", "S로 시작하고 '철자를 쓰는 사람'을 뜻합니다."]),
    puzzle("B", "두 개의 신호", "두 전력 신호 4와 4096을 더한 값을 이진수로 바꾸세요.", [
        "4 = 2²\n4096 = 2¹²\n\n따라서 두 자리값만 켜져 있습니다.",
        "이진수는 오른쪽부터 2⁰, 2¹, 2² … 자리를 뜻합니다. 2¹² 자리부터 2⁰ 자리까지 빠짐없이 적으세요.",
    ], "0과 1로 이루어진 13자리", ["1000000000100"], 1,
       "2¹²과 2² 자리만 1이고 나머지는 0이므로 1000000000100이다.",
       ["4100", "1000000001000", "10000000000100"],
       ["4와 4096을 십진수로 더하는 데서 끝나지 않습니다.", "2¹² 자리와 2² 자리만 켜집니다.", "맨 왼쪽과 오른쪽에서 세 번째 비트가 1입니다."]),
    puzzle("C", "색의 서가", "기록 일곱 개를 빨강→주황→노랑→초록→파랑→남색→보라 순서로 정렬하고, 각 기록의 마지막 글자를 읽으세요.", [
        "[파랑] 정원입\n[빨강] 이야기주\n[보라] 은하다\n[초록] 의문독",
        "[주황] 문명경\n[남색] 답변니\n[노랑] 제목야",
        "정렬한 기록의 첫 글자들은 '이 문제의 정답은'이 됩니다. 같은 순서로 마지막 글자를 읽으세요.",
    ], "한글 4글자", ["주경야독"], 1,
       "무지개 순서로 마지막 글자를 읽으면 주-경-야-독이다.",
       ["이문제의정답은", "주야경독", "정답은주경야독"],
       ["색은 무지개 순서입니다.", "첫 글자 순서가 정렬 검산값입니다.", "마지막 글자는 주, 경, 야, 독입니다."]),
    puzzle("D", "점과 선의 통역", "앞 신호를 영어 모스로 읽고 한글로 번역한 뒤, 뒤 자모와 '&'를 자연스럽게 연결하세요.", [
        "영어 모스\n.-.. / --- / ...- / .\n\n표\nL=.-..  O=---\nV=...-   E=.",
        "뒤 신호는 한글 자모로 이미 복원돼 있습니다.\nㅈㅓㄴ / ㅈㅐㅇ\n\n&는 한글 접속 조사로 읽습니다.",
        "최종 답은 영어를 남기지 않고 모두 한글로 씁니다.",
    ], "띄어쓰기 없는 한글", ["사랑과전쟁"], 1,
       "모스는 LOVE, 번역하면 사랑이다. 뒤 자모는 전쟁이고 &는 과이므로 사랑과전쟁이다.",
       ["loveandwar", "사랑전쟁", "러브와전쟁"],
       ["앞 모스는 LOVE입니다.", "LOVE를 한글 두 글자로 번역하세요.", "&를 '과'로 읽어 사랑+과+전쟁을 합치세요."]),
    puzzle("E", "여섯 점의 눈", "점자 셀의 점 번호를 자모표로 바꾸고 음절로 묶으세요.", [
        "점 번호\n1 4\n2 5\n3 6\n\n입력 셀\n46 · 234 · 26 / 46 · 126",
        "이 방에서 쓰는 자모표\n46=ㅈ\n234=ㅓ\n26=ㅁ\n126=ㅏ",
        "가운데 /는 음절 경계입니다. 앞의 세 자모와 뒤의 두 자모를 각각 한 음절로 조립하세요.",
    ], "한글 2글자", ["점자"], 2,
       "46·234·26은 ㅈㅓㅁ=점, 46·126은 ㅈㅏ=자다.",
       ["저마", "점마", "브라유"],
       ["숫자를 자모로 먼저 바꾸세요.", "앞부분은 ㅈ,ㅓ,ㅁ입니다.", "두 음절은 점/자입니다."]),
    puzzle("G", "네 수호자의 왕", "후보표에서 네 증거를 모두 만족하는 수호자를 찾으세요.", [
        "후보       영역   무기     상징   지위\n제우스     하늘   번개     독수리 왕\n포세이돈 바다   삼지창 말       왕의형제",
        "하데스     지하   투구     케르베로스 왕의형제\n아폴론     태양   활       월계수 예언자",
        "증거: 하늘을 다스리고, 번개를 들며, 독수리를 상징으로 삼고, 신들의 왕이라 불립니다.",
    ], "후보의 한글 이름", ["제우스"], 2,
       "네 열이 모두 하늘/번개/독수리/왕인 유일한 행은 제우스다.",
       ["포세이돈", "하데스", "아폴론"],
       ["증거 하나가 아니라 네 열을 모두 대조하세요.", "영역은 하늘입니다.", "첫 번째 후보만 네 조건을 모두 만족합니다."]),
    puzzle("J", "조커 없는 패", "다섯 장의 카드가 만드는 가장 높은 포커 패를 판정하세요.", [
        "패\n5♥  5♣  3♠  9♦  K♥",
        "판정표(높은 쪽 우선)\n포카드: 같은 숫자 4장\n트리플: 같은 숫자 3장\n투페어: 숫자 쌍 2개\n원페어: 숫자 쌍 1개\n하이카드: 그 외",
        "무늬가 같아야 하는 플러시나 연속 숫자인 스트레이트 조건은 만족하지 않습니다.",
    ], "영어 또는 한글 포커 패 이름", ["onepair", "원페어"], 2,
       "숫자 5가 정확히 두 장이고 다른 숫자는 모두 한 장이므로 one pair다.",
       ["twopair", "트리플", "highcard"],
       ["같은 숫자의 장수를 세세요.", "5가 두 장입니다.", "숫자 쌍 하나의 패입니다."]),
    puzzle("K", "기사의 다섯 착지", "체스 나이트가 지정 경로로 이동할 때 착지 칸의 글자를 순서대로 읽으세요.", [
        "좌표는 왼쪽부터 A~E, 아래부터 1~5입니다.\n\n5: · · R · ·\n4: T · · · A\n3: · · · · ·",
        "2: · · · E ·\n1: · H · · ·\n   A B C D E",
        "경로\nB1 → D2 → E4 → C5 → A4\n\n모든 이동은 가로 2+세로 1 또는 가로 1+세로 2인 나이트 이동입니다.",
    ], "영어 5글자", ["heart"], 2,
       "지정 경로의 착지 글자는 순서대로 H,E,A,R,T이므로 HEART다.",
       ["earth", "hater", "horse"],
       ["시작 칸의 글자도 포함합니다.", "착지는 H-E-A-R-T 순서입니다.", "심장을 뜻하는 영어 단어입니다."]),
    puzzle("L", "일곱 선분 기록", "선분 이름표와 점등 목록을 이용해 다섯 글자를 읽으세요.", [
        "선분 배치\n aaa\nf   b\n ggg\ne   c\n ddd",
        "점등 목록\n1: a f g c d\n2: a b c d e f\n3: b c d e f\n4: c e g\n5: b c d e g",
        "각 목록을 실제 선분 모양으로 그리면 영문자처럼 보입니다. 대문자·소문자 모양을 함께 허용해 왼쪽부터 읽으세요.",
    ], "영어 5글자", ["sound"], 3,
       "점등 모양은 차례대로 S,O,U,n,d이므로 SOUND다.",
       ["south", "round", "seven"],
       ["첫 글자는 S입니다.", "가운데 세 글자는 O-U-N입니다.", "S O U N D로 읽힙니다."]),
    puzzle("M", "두 악보의 먹이", "서로 다른 음이름 규칙으로 두 악보를 문자화한 뒤, 두 단어가 가리키는 동물을 찾으세요.", [
        "서양 음이름\n도=C 레=D 미=E 파=F 솔=G 라=A 시=B\n\n악보 1: 파 미 미 레",
        "두 번째 기록의 전용 음절표\n파=BA, 시=NA\n\n악보 2: 파 시 시",
        "첫 단어는 '먹이를 주다', 두 번째 단어는 과일입니다. 그 과일을 먹이로 떠올릴 수 있는 대표 동물을 영어로 쓰세요.",
    ], "영어 동물 이름", ["monkey", "원숭이"], 3,
       "파미미레=FEED, 파시시=BANANA다. FEED BANANA가 가리키는 대표 동물은 MONKEY다.",
       ["banana", "gorilla", "feed"],
       ["첫 악보는 FEED입니다.", "두 번째 악보는 BANANA입니다.", "바나나를 먹는 대표 동물을 떠올리세요."]),
    puzzle("N", "찢어진 깃발", "네 조각을 합쳤을 때 만들어지는 국기를 후보표에서 고르세요.", [
        "조각 정보\n- 바탕 조각 네 개는 모두 파랑\n- 가운데를 가르는 두 띠는 흰색\n- 띠는 +가 아니라 대각선 X",
        "후보\n자메이카: 초록/검정 + 노랑 X\n스코틀랜드: 파랑 + 흰 X\n핀란드: 흰색 + 파랑 +\n잉글랜드: 흰색 + 빨강 +",
        "색 두 가지와 교차 방향을 모두 만족해야 합니다.",
    ], "국가·지역의 한글 이름", ["스코틀랜드", "scotland"], 3,
       "파란 바탕과 흰 대각선 X를 모두 만족하는 후보는 스코틀랜드다.",
       ["핀란드", "잉글랜드", "자메이카"],
       ["십자가의 방향부터 구분하세요.", "대각선 X입니다.", "파랑 바탕+흰 X인 후보입니다."]),
    puzzle("O", "네 개의 시계 좌표", "짧은 바늘과 긴 바늘이 가리키는 좌표로 네 글자를 꺼내 장소를 뜻하는 영어 단어를 만드세요.", [
        "문자판\n      0  12 24 36 48\n1시  A  B  C  D  E\n2시  F  G  H  I  J\n3시  K  L  M  N  O\n4시  P  Q  R  S  T\n5시  U  V  W  X  Y",
        "시계 기록\n① 짧은 1 / 긴 24\n② 짧은 2 / 긴 36\n③ 짧은 4 / 긴 48\n④ 짧은 5 / 긴 48",
        "짧은 바늘은 행의 시각, 긴 바늘은 열 머리의 분 눈금을 고릅니다. 얻은 네 글자를 기록 순서대로 이으세요.",
    ], "영어 4글자", ["city"], 3,
       "(1,24)=C, (2,36)=I, (4,48)=T, (5,48)=Y이므로 CITY다.",
       ["time", "clock", "civy"],
       ["짧은 바늘로 행을 먼저 찾으세요.", "첫 두 좌표는 C와 I입니다.", "마지막 두 좌표는 T와 Y입니다."]),
    puzzle("P", "원소 번호 압축", "예시와 같은 규칙으로 H₂O를 숫자열로 바꾸세요.", [
        "필요한 원자번호\nH=1  C=6  O=8\nW=74  At=85  Er=68",
        "규칙: 아래첨자는 원소 기호를 그 횟수만큼 반복하고, 각 원자번호를 구분자 없이 잇습니다.\n\n예: CO₂ → C,O,O → 6,8,8 → 688",
        "검산 장식: W+At+Er를 문자로 이어 읽으면 WATER입니다. 이제 H₂O를 같은 규칙으로 바꾸세요.",
    ], "숫자 3자리", ["118"], 4,
       "H₂O는 H,H,O이고 원자번호는 1,1,8이므로 118이다.",
       ["18", "1108", "288"],
       ["아래첨자 2는 H를 두 번 사용한다는 뜻입니다.", "H,H,O로 펼치세요.", "원자번호 1,1,8을 이어 쓰세요."]),
    puzzle("Q", "오른쪽으로 밀린 기록", "고장 규칙을 검산문으로 판별하고, 목표 기록을 원래 누른 여섯 글자로 복원하세요.", [
        "표준 영문 자판의 윗글쇠 행을 사용합니다. 단말은 누른 키가 아니라 바로 오른쪽 이웃 키를 기록합니다.",
        "검산문\n원래: TEST\n기록: YRDY\n\nT의 오른쪽은 Y, E의 오른쪽은 R, S의 오른쪽은 D입니다.",
        "목표 기록\nWERTYU\n\n각 글자를 윗글쇠 행에서 한 칸 왼쪽으로 되돌리세요.",
    ], "영어 6글자", ["qwerty"], 4,
       "W,E,R,T,Y,U의 왼쪽 이웃은 차례대로 Q,W,E,R,T,Y이므로 QWERTY다.",
       ["wertyu", "asdfgh", "qwertyuiop"],
       ["검산문 TEST→YRDY에서 이동 방향을 확인하세요.", "기록 W의 왼쪽은 Q, E의 왼쪽은 W입니다.", "복원 결과는 Q-W-E-R-T-Y입니다."]),
    puzzle("R", "로마의 분할 기록", "세 수를 각각 로마 숫자로 바꾼 뒤, 변환 결과를 순서대로 이어 붙이세요.", [
        "기호표\nI=1  V=5  X=10\nL=50  C=100",
        "입력 묶음\n101 / 6 / 50\n\n각 묶음은 서로 독립입니다. 101은 100+1, 6은 5+1로 씁니다.",
        "세 변환 결과 사이에는 공백이나 구분자를 넣지 않습니다.",
    ], "영어 대문자 5글자", ["civil"], 4,
       "101=CI, 6=VI, 50=L이며 이어 붙이면 CIVIL이다.",
       ["civli", "clvi", "151"],
       ["101은 CI입니다.", "6은 VI, 50은 L입니다.", "CI+VI+L을 이어 쓰세요."]),
    puzzle("S", "여덟 번째 궤도", "궤도와 봉인 문양을 함께 해독해 마지막 행성의 한글 이름을 복원하세요.", [
        "궤도 기록\n수성 → 금성 → 지구 → 화성 → 목성 → 토성 → 천왕성 → ?",
        "마지막 행성 단서\n- 태양에서 여덟 번째\n- 얼음 거대 행성\n- 짙은 푸른빛\n- 천왕성보다 바깥 궤도",
        "이름 봉인\n海 = 바다를 뜻하며 음은 '해'\n王 = 임금을 뜻하며 음은 '왕'\n星 = 별을 뜻하며 음은 '성'\n\n세 음을 순서대로 붙이세요.",
    ], "한글 행성 이름 3글자", ["해왕성"], 4,
       "여덟 번째 얼음 거대 행성의 이름 봉인 海王星을 각각 해·왕·성으로 읽는다.",
       ["천왕성", "명왕성", "수성"],
       ["궤도 목록의 마지막 칸입니다.", "봉인 문양은 海-王-星 세 글자입니다.", "각 음은 해-왕-성입니다."]),
    puzzle("T", "열두 지지의 합성", "수수께끼가 가리키는 같은 순번의 지지와 동물을 각각 찾아 붙이세요.", [
        "지지 순서\n자  축  인  묘  진  사\n오  미  신  유  술  해",
        "동물 순서\n쥐  소  호랑이  토끼  용  뱀\n말  양  원숭이  닭  개  돼지",
        "순번 수수께끼\n나는 첫째 쥐의 바로 뒤이며 셋째 호랑이의 바로 앞이다. 같은 순번에서 지지 한 글자와 동물 이름을 꺼내 그 순서로 붙이세요.",
    ], "한글 2글자", ["축소"], 4,
       "수수께끼의 순번은 둘째다. 지지의 둘째는 축, 동물의 둘째는 소이므로 축소다.",
       ["소축", "축우", "자쥐"],
       ["두 줄에서 같은 순번을 사용합니다.", "수수께끼는 둘째를 뜻합니다.", "지지 둘째 뒤에 동물 둘째를 붙이세요."]),
    puzzle("W", "요일 교차 행렬", "네 봉인이 가리키는 요일의 천체·오행을 행과 열로 바꿔 글자를 추출하세요.", [
        "열:      木  金  土  日\n행 月:    M   A   R   T\n행 水:    O   I   B   C\n행 火:    D   E   S   F",
        "봉인 기록\n① 월요일의 천체 × 목요일의 오행\n② 수요일의 오행 × 금요일의 오행\n③ 화요일의 오행 × 토요일의 오행\n④ 월요일의 천체 × 일요일의 천체",
        "× 앞은 행, 뒤는 열입니다. 요일 이름에서 月·水·火·木·金·土·日 기호를 찾아 네 글자를 순서대로 이으세요.",
    ], "영어 4글자", ["mist"], 5,
       "좌표값은 M,I,S,T이므로 MIST다.",
       ["mars", "mice", "most"],
       ["행과 열 순서를 바꾸지 마세요.", "첫 두 좌표는 M, I입니다.", "마지막 두 좌표는 S, T입니다."]),
    puzzle("Y", "되돌아오는 길", "통로표에서 START→END의 유일한 길을 찾고, 같은 길로 귀환해 회문을 완성하세요.", [
        "양방향 통로\nSTART-R   R-O   R-E\nE-K       O-T   T-A\nT-I       I-N   A-V\nV-END\n\nK와 N에서는 더 이어지는 통로가 없습니다.",
        "START에서 방을 반복하지 않고 END까지 갑니다. 지나간 알파벳 방을 순서대로 기록하세요.",
        "END에서 같은 길로 돌아오되 방향 전환점 V는 한 번만 적습니다. 완성 답은 영어 9글자이며 거꾸로 읽어도 같습니다.",
    ], "영어 9글자 회문", ["rotavator"], 5,
       "유일한 전진 길은 R-O-T-A-V다. V를 중복하지 않은 귀환 A-T-O-R을 붙이면 ROTAVATOR다.",
       ["rotavvator", "rotator", "rotavatora"],
       ["R에서 E 쪽은 막다른 길입니다.", "전진 기록은 R-O-T-A-V입니다.", "귀환은 V를 빼고 A-T-O-R입니다."]),
    puzzle("Z", "열세 번째 황도", "관측 위치와 그리스어 어근을 결합해 전통 12궁 밖의 열세 번째 별자리 이름을 만드세요.", [
        "황도 관측 기록\n- 전갈자리와 사수자리 사이\n- 태양이 매년 이 구역을 통과함\n- 전통 12궁에는 포함되지 않음\n- 사람 형태가 긴 뱀을 붙든 모습",
        "어근 기록\nOPHIS = 뱀\nEKHEIN = 붙들다\n\n이 어근을 합친 고대 이름은 '뱀을 붙든 사람'을 뜻합니다.",
        "한국어 조립 규칙\n[붙든 대상] + [그 대상의 주체] + 자리\n\n무언가를 소유하거나 맡은 사람을 뜻하는 두 글자 낱말을 가운데에 넣으세요.",
    ], "한글 별자리 이름 5글자", ["뱀주인자리"], 5,
       "붙든 대상은 뱀, 그 주체는 주인이고 별자리 접미사 자리를 붙여 뱀주인자리가 된다.",
       ["사수자리", "전갈자리", "뱀자리"],
       ["OPHIS가 가리키는 대상을 먼저 번역하세요.", "가운데 두 글자는 소유·담당자를 뜻합니다.", "뱀 + 주인 + 자리를 합치세요."]),
]


def set_cell(grid: list[list[str]], x: int, z: int, value: str) -> None:
    if 0 <= x < 15 and 0 <= z < 15:
        grid[z][x] = value


def line(grid: list[list[str]], x0: int, z0: int, x1: int, z1: int, value: str) -> None:
    dx, dz = abs(x1 - x0), abs(z1 - z0)
    sx, sz = (1 if x0 < x1 else -1), (1 if z0 < z1 else -1)
    error = dx - dz
    while True:
        set_cell(grid, x0, z0, value)
        if x0 == x1 and z0 == z1:
            return
        twice = 2 * error
        if twice > -dz:
            error -= dz
            x0 += sx
        if twice < dx:
            error += dx
            z0 += sz


def thematic_pattern(index: int) -> list[str]:
    grid = [["." for _ in range(15)] for _ in range(15)]
    for offset in range(15):
        set_cell(grid, offset, 0, "#")
        set_cell(grid, offset, 14, "#")
        set_cell(grid, 0, offset, "#")
        set_cell(grid, 14, offset, "#")
    theme = PUZZLES[index]["letter"]
    if theme == "A":
        for x, z in [(3, 3), (11, 3), (7, 6), (4, 10), (10, 11)]: set_cell(grid, x, z, "C")
        line(grid, 3, 3, 10, 11, "P"); line(grid, 11, 3, 4, 10, "P")
    elif theme == "B":
        for x in (3, 11): line(grid, x, 2, x, 12, "C")
        for x in range(5, 10): set_cell(grid, x, 7, "G")
    elif theme == "C":
        for z, color in enumerate("ROYLGBC", 3): line(grid, 2, z, 12, z, color)
    elif theme == "D":
        for x in (2, 4, 10, 12): set_cell(grid, x, 5, "W")
        line(grid, 6, 5, 8, 5, "W"); line(grid, 3, 10, 6, 10, "C"); set_cell(grid, 10, 10, "C")
    elif theme == "E":
        for x, z in [(5, 3), (9, 3), (5, 7), (9, 7), (5, 11), (9, 11)]: set_cell(grid, x, z, "W")
    elif theme == "G":
        line(grid, 9, 2, 5, 8, "Y"); line(grid, 5, 8, 9, 8, "Y"); line(grid, 9, 8, 5, 13, "Y")
    elif theme == "J":
        for x0, color in [(2, "R"), (8, "W")]:
            for x in range(x0, x0 + 5):
                for z in range(3, 12):
                    if x in (x0, x0 + 4) or z in (3, 11): set_cell(grid, x, z, color)
    elif theme == "K":
        points = [(3, 11), (7, 9), (10, 5), (6, 3), (2, 5)]
        for a, b in zip(points, points[1:]): line(grid, *a, *b, "C")
        for x, z in points: set_cell(grid, x, z, "G")
    elif theme == "L":
        for args in [(4, 2, 10, 2), (4, 7, 10, 7), (4, 12, 10, 12), (3, 3, 3, 6), (11, 8, 11, 11)]: line(grid, *args, "R")
    elif theme == "M":
        line(grid, 8, 3, 8, 10, "P"); line(grid, 8, 3, 12, 2, "P")
        for x, z in [(6, 10), (7, 11), (8, 10), (11, 9), (12, 10), (11, 11)]: set_cell(grid, x, z, "P")
    elif theme == "N":
        line(grid, 2, 2, 12, 12, "W"); line(grid, 12, 2, 2, 12, "W")
        for z in range(2, 13):
            for x in range(2, 13):
                if grid[z][x] == ".": grid[z][x] = "B"
    elif theme == "O":
        for x, z in [(7, 2), (11, 4), (12, 7), (11, 10), (7, 12), (3, 10), (2, 7), (3, 4)]: set_cell(grid, x, z, "C")
        line(grid, 7, 7, 7, 2, "G"); line(grid, 7, 7, 11, 10, "G")
    elif theme == "P":
        for x in range(3, 13, 3): line(grid, x, 2, x, 12, "C")
        for z in range(3, 13, 3): line(grid, 2, z, 12, z, "C")
        set_cell(grid, 5, 5, "G"); set_cell(grid, 8, 8, "G")
    elif theme == "Q":
        for z, width in [(3, 10), (6, 10), (9, 9), (12, 7)]:
            for x in range((15 - width) // 2, (15 - width) // 2 + width): set_cell(grid, x, z, "C" if z != 6 else "G")
    elif theme == "R":
        line(grid, 3, 3, 3, 11, "W"); line(grid, 7, 3, 7, 11, "W"); line(grid, 11, 3, 11, 11, "W")
        line(grid, 9, 3, 11, 7, "R"); line(grid, 13, 3, 11, 7, "R")
    elif theme == "S":
        for radius, color in [(2, "C"), (4, "B"), (6, "P")]:
            for step in range(32):
                angle = 2 * math.pi * step / 32
                set_cell(grid, round(7 + radius * math.cos(angle)), round(7 + radius * math.sin(angle)), color)
        set_cell(grid, 7, 7, "Y")
    elif theme == "T":
        zodiac = [
            (round(7 + 5 * math.cos(2 * math.pi * step / 12)),
             round(7 + 5 * math.sin(2 * math.pi * step / 12)))
            for step in range(12)
        ]
        for start, end in zip(zodiac, zodiac[1:] + zodiac[:1]): line(grid, *start, *end, "C")
        for x, z in zodiac: set_cell(grid, x, z, "G")
    elif theme == "W":
        for x in (3, 6, 9, 12): line(grid, x, 2, x, 12, "C")
        for z in (3, 6, 9, 12): line(grid, 2, z, 12, z, "C")
        for x, z in [(3, 3), (6, 6), (9, 9), (12, 3)]: set_cell(grid, x, z, "G")
    elif theme == "Y":
        line(grid, 2, 2, 12, 2, "C"); line(grid, 12, 2, 12, 12, "C"); line(grid, 12, 12, 4, 12, "C"); line(grid, 4, 12, 4, 6, "C"); line(grid, 4, 6, 9, 6, "C")
        set_cell(grid, 2, 2, "L"); set_cell(grid, 9, 6, "R")
    elif theme == "Z":
        points = [(2, 9), (4, 4), (7, 2), (10, 5), (12, 10), (8, 12), (5, 9)]
        for a, b in zip(points, points[1:]): line(grid, *a, *b, "P")
        for x, z in points: set_cell(grid, x, z, "W")
    return ["".join(row) for row in grid]


def position(x: int, y: int, z: int, yaw: float = 0.0) -> dict:
    return {"x": x, "y": y, "z": z, "yaw": yaw, "pitch": 0.0}


def make_visual(center_x: int, minimum_z: int, rows: list[str]) -> dict:
    used = sorted({character for row in rows for character in row}, key=lambda value: list(MATERIALS).index(value))
    tile_by_character = {character: index for index, character in enumerate(used)}
    return {
        "origin": position(center_x - 15, 64, minimum_z + 10),
        "scale": 2,
        "width": 15,
        "height": 15,
        "palette": [
            {"tile": tile_by_character[character], "material": MATERIALS[character]}
            for character in used
        ],
        "cells": [tile_by_character[character] for row in rows for character in row],
    }


def make_room(index: int, definition: dict) -> dict:
    sequence = index + 1
    column, row = index % 4, index // 4
    center_x = -96 + column * 64
    minimum_z = row * 64
    minimum = position(center_x - 24, 60, minimum_z)
    maximum = position(center_x + 24, 78, minimum_z + 48)
    terminal = {
        "id": f"archive-{definition['letter'].lower()}",
        "type": "LOGIC_ANSWER",
        "question": definition["question"],
        "pages": definition["pages"],
        "answerFormat": definition["answerFormat"],
        "answers": definition["answers"],
        "normalization": "LETTERS_AND_DIGITS",
        "cooldownSeconds": 8 + definition["difficulty"] * 2,
        "difficulty": definition["difficulty"],
        "inspiration": f"A to Z v8 · {definition['letter']}",
        "solutionExplanation": definition["explanation"],
        "wrongAnswerSamples": definition["wrong"],
    }
    return {
        "id": f"archive-{definition['letter'].lower()}",
        "sequence": sequence,
        "originalStage": ord(definition["letter"]) - ord("A") + 1,
        "title": f"{definition['letter']} · {definition['title']}",
        "buildBounds": {"min": minimum, "max": maximum},
        "playBounds": {"min": minimum, "max": maximum},
        "spawn": position(center_x, 65, minimum_z + 4),
        "checkpoint": position(center_x, 65, minimum_z + 4),
        "visual": make_visual(center_x, minimum_z, thematic_pattern(index)),
        "completionMode": "ALL_MECHANICS",
        "mechanics": [terminal],
        "hints": [{"tier": tier, "text": text} for tier, text in enumerate(definition["hints"], 1)],
        "messages": {
            "intro": "증거 기록서를 읽고 바닥 도식을 관찰한 뒤 /maze answer <정답>으로 제출하세요.",
            "completion": f"{definition['letter']} 기록 해독 완료.",
            "failure": "방 상태가 안전하게 복구되었습니다. 기록서를 다시 확인하세요.",
        },
        "reset": {
            "scope": "ROOM",
            "teleportPartyToCheckpoint": True,
            "restoreBlocks": True,
            "removeOwnedEntities": True,
            "clearMechanicState": True,
        },
    }


def build_pack() -> dict:
    assert len(PUZZLES) == 20
    return {
        "$schema": "https://mcpuzzle.dev/schema/map-pack.schema.json",
        "schemaVersion": 1,
        "mapVersion": "2.1.0-a20",
        "mazeId": "a-to-z-archive-20",
        "displayName": "A–Z 기록보관소: 20개의 방",
        "description": "[미궁] A to Z v8의 검증된 소재를 마인크래프트 관찰·기록형 퍼즐로 재구성",
        "locale": "ko-KR",
        "party": {"minPlayers": 1, "maxPlayers": 4},
        "world": {
            "mode": "GENERATED_VOID",
            "environment": "NORMAL",
            "bounds": {"min": position(-128, 48, -16), "max": position(128, 96, 320)},
            "floorY": 64,
            "generator": {
                "type": "ROOM_STACK",
                "roomSpacing": 64,
                "floorMaterial": "POLISHED_DEEPSLATE",
                "wallMaterial": "DEEPSLATE_BRICKS",
                "ceilingMaterial": "BLACK_CONCRETE",
                "lightMaterial": "SEA_LANTERN",
            },
        },
        "partySpawns": [position(-99, 65, 4), position(-97, 65, 4), position(-95, 65, 4), position(-93, 65, 4)],
        "source": {
            "format": "[미궁] A to Z v8 analysis",
            "sha256": "5a43f214bba54335e2d39c07c61afc9fd844e00e48bcfbf2a438fed2cbb2cebb",
        },
        "rooms": [make_room(index, definition) for index, definition in enumerate(PUZZLES)],
    }


def main() -> None:
    destination = Path(__file__).with_name("map.jsonc")
    serialized = json.dumps(build_pack(), ensure_ascii=False, indent=2) + "\n"
    for prohibited in ("PRESSURE_PLATE", "BUTTON", "LEVER"):
        assert prohibited not in serialized
    destination.write_text(serialized, encoding="utf-8", newline="\n")
    print(f"wrote {destination} with {len(PUZZLES)} rooms")


if __name__ == "__main__":
    main()

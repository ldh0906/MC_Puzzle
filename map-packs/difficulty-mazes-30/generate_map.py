from __future__ import annotations

import json
import math
from pathlib import Path

from interaction_design import device_design


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
        "대소문자와 공백은 무시합니다.",
    ], "영어 7글자", ["speller"], 4,
       "WILLIAMSHAKESPEARE와 IAMAWEAKISHSPELLER는 같은 문자 빈도를 가진다. 남은 문자를 재배열하면 SPELLER다.",
       ["speaker", "spellar", "letters"],
       ["문자 빈도를 세어 보세요.", "남은 글자는 EEllPRS입니다.", "S로 시작하고 '철자를 쓰는 사람'을 뜻합니다."]),
    puzzle("B", "두 개의 신호", "두 전력 신호 4와 4096을 더한 값을 이진수로 바꾸세요.", [
        "이진수는 오른쪽부터 2⁰, 2¹, 2² … 자리를 뜻합니다. 2¹² 자리부터 2⁰ 자리까지 빠짐없이 적으세요.",
    ], "0과 1로 이루어진 13자리", ["1000000000100"], 2,
       "2¹²과 2² 자리만 1이고 나머지는 0이므로 1000000000100이다.",
       ["4100", "1000000001000", "10000000000100"],
       ["방 안의 두 발전기 출력을 먼저 더하세요.", "각 출력은 하나의 2의 거듭제곱이며 오른쪽 끝은 2⁰입니다.", "2¹²와 2²만 1이고 나머지 자리는 0입니다."]),
    puzzle("C", "색의 서가", "기록 일곱 개를 빨강→주황→노랑→초록→파랑→남색→보라 순서로 정렬하고, 각 기록의 마지막 글자를 읽으세요.", [
        "[파랑] 정원입\n[빨강] 이야기주\n[보라] 은하다\n[초록] 의문독",
        "[주황] 문명경\n[남색] 답변니\n[노랑] 제목야",
    ], "한글 4글자", ["주경야독"], 1,
       "무지개 순서로 마지막 글자를 읽으면 주-경-야-독이다.",
       ["이문제의정답은", "주야경독", "정답은주경야독"],
       ["각 색 띠에 들어가 기록의 마지막 글자를 적어 두세요.", "섞인 기록을 빨강→주황→노랑→초록→파랑→남색→보라로 정렬하세요.", "마지막 글자는 주·경·야·독·입·니·다이며 앞 네 글자를 제출합니다."]),
    puzzle("D", "점과 선의 통역", "앞 신호를 영어 모스로 읽고 한글로 번역한 뒤, 뒤 자모와 '&'를 자연스럽게 연결하세요.", [
        "영어 모스\n.-.. / --- / ...- / .\n\n표\nL=.-..  O=---\nV=...-   E=.",
        "뒤 신호\nㅈㅓㄴ / ㅈㅐㅇ\n\n연결 기호\n&",
    ], "띄어쓰기 없는 한글", ["사랑과전쟁"], 3,
       "모스는 LOVE, 번역하면 사랑이다. 뒤 자모는 전쟁이고 &는 과이므로 사랑과전쟁이다.",
       ["loveandwar", "사랑전쟁", "러브와전쟁"],
       ["앞 모스는 LOVE입니다.", "LOVE를 한글 두 글자로 번역하세요.", "&를 '과'로 읽어 사랑+과+전쟁을 합치세요."]),
    {"letter": "E", "removed": True},
    puzzle("F", "꽃과 돌의 좌표", "지정된 여섯 달의 탄생화와 탄생석을 각각 행·열 번호로 바꾸고 봉인 격자에서 글자를 읽으세요.", [
        "월별 기록\n1월 카네이션/가넷\n4월 데이지/다이아몬드\n5월 은방울꽃/에메랄드\n6월 장미/진주\n8월 글라디올러스/페리도트\n11월 국화/토파즈",
        "꽃 행\n카네이션=1 데이지=2\n은방울꽃=3 장미=4\n글라디올러스=5 국화=6\n\n돌 열\n가넷=4 다이아몬드=2\n에메랄드=6 진주=1\n페리도트=3 토파즈=5",
        "봉인 격자\n1: A B C F D E\n2: G L H I J K\n3: L M N P Q O\n4: W R S T U V\n5: X Y E Z A B\n6: C D E F R G\n\n달 순서: 1→4→5→6→8→11",
    ], "영어 6글자", ["flower"], 3,
       "여섯 달의 꽃 행과 돌 열은 (1,4),(2,2),(3,6),(4,1),(5,3),(6,5)이며 F-L-O-W-E-R다.",
       ["flawer", "garden", "stone"],
       ["꽃 번호는 행, 돌 번호는 열입니다.", "첫 두 좌표는 (1,4), (2,2)입니다.", "좌표 글자는 F-L-O-W-E-R입니다."]),
    puzzle("G", "네 수호자의 왕", "하늘·번개·독수리·신들의 왕이라는 네 조건을 모두 만족하는 수호자를 찾으세요.", [
        "후보       영역   무기     상징   지위\n제우스     하늘   번개     독수리 왕\n포세이돈 바다   삼지창 말       왕의형제",
        "하데스     지하   투구     케르베로스 왕의형제\n아폴론     태양   활       월계수 예언자",
        "증거: 하늘을 다스리고, 번개를 들며, 독수리를 상징으로 삼고, 신들의 왕이라 불립니다.",
    ], "후보의 한글 이름", ["제우스"], 1,
       "네 열이 모두 하늘/번개/독수리/왕인 유일한 행은 제우스다.",
       ["포세이돈", "아테나", "하데스"],
       ["입구 조건판과 네 후보 전시대의 네 줄을 대조하세요.", "하늘과 번개뿐 아니라 상징과 지위까지 모두 맞아야 합니다.", "하늘·번개·독수리·신들의 왕을 모두 만족하는 후보는 제우스입니다."]),
    puzzle("H", "웃음의 한 글자", "세 영어 쌍의 공통 규칙을 적용해 FUN의 짝을 찾으세요.", [
        "보기\nFEAR ↔ PEAR\nFIG ↔ PIG\nFACE ↔ PACE\n\n목표\nFUN ↔ ?",
    ], "영어 3글자", ["pun"], 1,
       "각 쌍은 첫 글자 F를 P로 바꾸므로 FUN의 짝은 PUN이다.",
       ["fun", "pear", "humor"],
       ["방 안의 세 보기에서 바뀐 글자의 위치를 먼저 비교하세요.", "세 보기 모두 첫 글자에 같은 F→P 변화가 일어납니다.", "FUN의 F를 P로 바꾼 PUN이 되므로 P 버튼입니다."]),
    puzzle("I", "백 번째 순환", "무한소수 1/7에서 소수점 이하 100번째 자리부터 연속 여섯 자리를 읽으세요.", [
        "관측 기록\n1/7 = 0.142857142857142857…\n\n소수점 바로 다음 숫자를 1번째 자리로 셉니다.",
    ], "숫자 6자리", ["857142"], 3,
       "순환마디는 142857이고 100=6×16+4이므로 4번째부터 한 주기는 857142다.",
       ["142857", "714285", "571428"],
       ["반복되는 최소 묶음의 길이를 찾으세요.", "100을 순환 길이 6으로 나눈 나머지는 4입니다.", "4번째 숫자부터 한 주기는 857142입니다."]),
    puzzle("J", "조커 없는 패", "다섯 장의 카드가 만드는 가장 높은 포커 패를 판정하세요.", [
        "패\n5♥  5♣  3♠  9♦  K♥",
        "판정표(높은 쪽 우선)\n포카드: 같은 숫자 4장\n트리플: 같은 숫자 3장\n투페어: 숫자 쌍 2개\n원페어: 숫자 쌍 1개\n하이카드: 그 외",
    ], "영어 또는 한글 포커 패 이름", ["onepair", "원페어"], 1,
       "숫자 5가 정확히 두 장이고 다른 숫자는 모두 한 장이므로 one pair다.",
       ["twopair", "트리플", "highcard"],
       ["다섯 카드와 방 안의 족보표를 함께 확인하세요.", "각 숫자의 장수를 세면 하나의 숫자만 두 번 등장합니다.", "두 장의 5를 선택하면 가장 높은 족보는 원페어입니다."]),
    puzzle("K", "달빛 기사의 봉인", "START에서 기사 이동만 사용해 금빛 봉인 다섯 개를 하나의 길로 이으세요.", [
        "기사는 한 방향으로 두 칸 간 뒤 옆으로 한 칸 꺾는 L자 모양으로 이동합니다.",
        "방 안에서 금빛으로 빛나는 다섯 발판만 봉인입니다. START에서 시작해 기사 이동으로 이어지는 봉인을 찾으세요.",
        "착지한 봉인의 글자는 액션바에서 하나씩 깨어납니다.",
    ], "영어 5글자", ["heart"], 2,
       "금빛 봉인을 기사 이동으로 잇는 유일한 경로는 B1→D2→E4→C5→A4이고, 착지 글자는 H,E,A,R,T이다.",
       ["earth", "hater", "horse"],
       ["체스판 앞 START 표식이 붙은 금빛 발판부터 시작하세요.", "현재 봉인에서 L자로 닿는 다른 금빛 봉인은 매번 하나뿐입니다.", "착지 글자는 H-E-A-R-T이며 경로는 B1→D2→E4→C5→A4입니다."]),
    puzzle("L", "일곱 선분 기록", "선분 이름표와 점등 목록을 이용해 다섯 글자를 읽으세요.", [
        "선분 배치\n aaa\nf   b\n ggg\ne   c\n ddd",
        "점등 목록\n1: a f g c d\n2: a b c d e f\n3: b c d e f\n4: c e g\n5: b c d e g",
    ], "영어 5글자", ["sound"], 3,
       "점등 모양은 차례대로 S,O,U,n,d이므로 SOUND다.",
       ["south", "round", "seven"],
       ["첫 글자는 S입니다.", "가운데 세 글자는 O-U-N입니다.", "S O U N D로 읽힙니다."]),
    puzzle("M", "두 악보의 먹이", "서로 다른 음이름 규칙으로 두 악보를 문자화한 뒤, 두 단어가 가리키는 동물을 찾으세요.", [
        "서양 음이름\n도=C 레=D 미=E 파=F 솔=G 라=A 시=B\n\n악보 1: 파 미 미 레",
        "두 번째 기록의 전용 음절표\n파=BA, 시=NA\n\n악보 2: 파 시 시",
    ], "영어 동물 이름", ["monkey", "원숭이"], 3,
       "파미미레=FEED, 파시시=BANANA다. FEED BANANA가 가리키는 대표 동물은 MONKEY다.",
       ["banana", "gorilla", "feed"],
       ["첫 악보는 FEED입니다.", "두 번째 악보는 BANANA입니다.", "바나나를 먹는 대표 동물을 떠올리세요."]),
    puzzle("N", "찢어진 깃발", "네 조각을 합쳤을 때 만들어지는 국기를 후보표에서 고르세요.", [
        "조각 정보\n- 바탕 조각 네 개는 모두 파랑\n- 가운데를 가르는 두 띠는 흰색\n- 띠는 +가 아니라 대각선 X",
        "후보\n자메이카: 초록/검정 + 노랑 X\n스코틀랜드: 파랑 + 흰 X\n핀란드: 흰색 + 파랑 +\n잉글랜드: 흰색 + 빨강 +",
    ], "국가·지역의 한글 이름", ["스코틀랜드", "scotland"], 1,
       "파란 바탕과 흰 대각선 X를 모두 만족하는 후보는 스코틀랜드다.",
       ["핀란드", "잉글랜드", "자메이카"],
       ["조각 제단에서 바탕 조각과 띠 조각의 색을 따로 확인하세요.", "두 흰 띠는 +가 아니라 대각선 X로 교차합니다.", "파란 바탕에 흰 X가 있는 후보 III가 스코틀랜드입니다."]),
    puzzle("O", "네 개의 시계 좌표", "짧은 바늘과 긴 바늘이 가리키는 좌표로 네 글자를 꺼내 장소를 뜻하는 영어 단어를 만드세요.", [
        "문자판\n      0  12 24 36 48\n1시  A  B  C  D  E\n2시  F  G  H  I  J\n3시  K  L  M  N  O\n4시  P  Q  R  S  T\n5시  U  V  W  X  Y",
        "시계 기록\n① 짧은 1 / 긴 24\n② 짧은 2 / 긴 36\n③ 짧은 4 / 긴 48\n④ 짧은 5 / 긴 48",
        "좌표 규칙\n짧은 바늘 = 행\n긴 바늘 = 열",
    ], "영어 4글자", ["city"], 2,
       "(1,24)=C, (2,36)=I, (4,48)=T, (5,48)=Y이므로 CITY다.",
       ["time", "clock", "civy"],
       ["각 시계 옆 짧은 바늘 표지와 바닥에서 빛나는 긴 바늘 칸을 확인하세요.", "짧은 바늘은 문자판의 행, 빛난 긴 바늘 값은 열입니다.", "열 값은 24→36→48→48이고 교차 글자는 C-I-T-Y입니다."]),
    puzzle("P", "원소 번호 압축", "예시와 같은 규칙으로 H₂O를 숫자열로 바꾸세요.", [
        "필요한 원자번호\nH=1  C=6  O=8\nW=74  At=85  Er=68",
        "규칙: 아래첨자는 원소 기호의 개수입니다. 원자번호는 구분자 없이 잇습니다.\n\n예: CO₂ → C,O,O → 6,8,8 → 688",
    ], "숫자 3자리", ["118"], 2,
       "H₂O는 H,H,O이고 원자번호는 1,1,8이므로 118이다.",
       ["18", "1108", "288"],
       ["물 분자 모형·화학식과 CO₂ 변환 예시를 함께 보세요.", "아래첨자 2는 바로 앞 원소가 두 개라는 뜻입니다.", "H₂O를 H,H,O로 펼치고 원자번호 1,1,8을 이어 입력하세요."]),
    puzzle("Q", "오른쪽으로 밀린 기록", "고장 규칙을 검산문으로 판별하고, 목표 기록을 원래 누른 여섯 글자로 복원하세요.", [
        "사용 장치\n표준 영문 자판의 윗글쇠 행",
        "검산문\n원래: TEST\n기록: YRDY",
        "목표 기록\nWERTYU",
    ], "영어 6글자", ["qwerty"], 3,
       "W,E,R,T,Y,U의 왼쪽 이웃은 차례대로 Q,W,E,R,T,Y이므로 QWERTY다.",
       ["wertyu", "asdfgh", "qwertyuiop"],
       ["검산문 TEST→YRDY에서 이동 방향을 확인하세요.", "기록 W의 왼쪽은 Q, E의 왼쪽은 W입니다.", "복원 결과는 Q-W-E-R-T-Y입니다."]),
    puzzle("R", "로마의 분할 기록", "세 수를 각각 로마 숫자로 바꾼 뒤, 변환 결과를 순서대로 이어 붙이세요.", [
        "기호표\nI=1  V=5  X=10\nL=50  C=100",
        "입력 묶음\n101 / 6 / 50\n\n각 묶음은 서로 독립입니다.",
    ], "영어 대문자 5글자", ["civil"], 2,
       "101=CI, 6=VI, 50=L이며 이어 붙이면 CIVIL이다.",
       ["civli", "clvi", "151"],
       ["세 수의 제단과 양쪽 로마 숫자 기호값 패널을 확인하세요.", "101은 100+1, 6은 5+1이며 각 묶음은 독립입니다.", "101=CI, 6=VI, 50=L이므로 CI+VI+L입니다."]),
    puzzle("S", "여덟 번째 궤도", "궤도와 봉인 문양을 함께 해독해 마지막 행성의 한글 이름을 복원하세요.", [
        "궤도 기록\n수성 → 금성 → 지구 → 화성 → 목성 → 토성 → 천왕성 → ?",
        "마지막 행성 단서\n- 태양에서 여덟 번째\n- 얼음 거대 행성\n- 짙은 푸른빛\n- 천왕성보다 바깥 궤도",
        "이름 봉인\n海 = 바다\n王 = 임금\n星 = 별",
    ], "한글 행성 이름 3글자", ["해왕성"], 3,
       "여덟 번째 얼음 거대 행성의 이름 봉인 海王星을 각각 해·왕·성으로 읽는다.",
       ["천왕성", "명왕성", "수성"],
       ["궤도 목록의 마지막 칸입니다.", "봉인 문양은 海-王-星 세 글자입니다.", "각 음은 해-왕-성입니다."]),
    puzzle("T", "열두 지지의 합성", "수수께끼가 가리키는 같은 순번의 지지와 동물을 각각 찾아 붙이세요.", [
        "지지 순서\n자  축  인  묘  진  사\n오  미  신  유  술  해",
        "동물 순서\n쥐  소  호랑이  토끼  용  뱀\n말  양  원숭이  닭  개  돼지",
        "순번 수수께끼\n나는 첫째 쥐의 바로 뒤이며 셋째 호랑이의 바로 앞이다. 같은 순번에서 지지 한 글자와 동물 이름을 꺼내 그 순서로 붙이세요.",
    ], "한글 2글자", ["축소"], 3,
       "수수께끼의 순번은 둘째다. 지지의 둘째는 축, 동물의 둘째는 소이므로 축소다.",
       ["소축", "축우", "자쥐"],
       ["두 줄에서 같은 순번을 사용합니다.", "수수께끼는 둘째를 뜻합니다.", "지지 둘째 뒤에 동물 둘째를 붙이세요."]),
    puzzle("U", "두 버튼의 주인", "스스로 이동하지 못하고 수리·채집·수송 기능이 없으며 적을 공격하는 방어 건물을 찾으세요.", [
        "후보 명령 목록\n해병: 이동/정지/공격/순찰\n일꾼: 이동/정지/공격/수리/채집\n벙커: 적재/하역/집결\n미사일 터렛: 정지/공격",
        "대상 기록\n- 스스로 이동하지 못함\n- 적을 공격할 수 있음\n- 수리·채집 기능 없음\n- 적재·하역 기능 없음",
    ], "한글 또는 영어 유닛 이름", ["미사일터렛", "missileturret"], 1,
       "정지와 공격만 가진 유일한 후보는 미사일 터렛이다.",
       ["해병", "일꾼", "벙커"],
       ["대상 기록과 네 후보의 실제 명령 패널을 비교하세요.", "이동·수리·채집·적재·하역 중 하나라도 있으면 제외하세요.", "조건을 모두 만족해 정지와 공격만 남는 후보는 미사일 터렛입니다."]),
    puzzle("V", "자음과 모음 보관함", "같은 번호의 초성·중성·종성을 결합해 두 음절을 만드세요.", [
        "초성 보관함\n2=ㄱ  1=ㅁ\n\n중성 보관함\n2=ㅜ  1=ㅣ\n\n종성 보관함\n2=ㅇ  1=없음",
        "조립 순서\n1번 초성+중성+종성\n2번 초성+중성+종성",
    ], "한글 2글자", ["미궁"], 2,
       "1번은 ㅁ+ㅣ=미, 2번은 ㄱ+ㅜ+ㅇ=궁이므로 미궁이다.",
       ["궁미", "미구", "기뭉"],
       ["방 안의 초성·중성·종성 보관함 세 곳을 확인하세요.", "같은 번호의 자모를 초성→중성→종성으로 묶고 1번 뒤에 2번을 놓으세요.", "1번은 미, 2번은 궁이므로 입력은 ㅁ·ㅣ·없음 / ㄱ·ㅜ·ㅇ입니다."]),
    puzzle("W", "요일 교차 행렬", "네 봉인이 가리키는 요일의 천체·오행을 행과 열로 바꿔 글자를 추출하세요.", [
        "열:      木  金  土  日\n행 月:    M   A   R   T\n행 水:    O   I   B   C\n행 火:    D   E   S   F",
        "봉인 기록\n① 월요일의 천체 × 목요일의 오행\n② 수요일의 오행 × 금요일의 오행\n③ 화요일의 오행 × 토요일의 오행\n④ 월요일의 천체 × 일요일의 천체\n\n× 앞 = 행 / 뒤 = 열",
    ], "영어 4글자", ["mist"], 4,
       "좌표값은 M,I,S,T이므로 MIST다.",
       ["mars", "mice", "most"],
       ["행과 열 순서를 바꾸지 마세요.", "첫 두 좌표는 M, I입니다.", "마지막 두 좌표는 S, T입니다."]),
    puzzle("X", "세 축의 항해", "원점에서 벡터 명령을 차례대로 수행하고, 매번 도착한 좌표의 글자를 읽으세요.", [
        "좌표 기록\n(-1,1,2)=R\n(0,2,2)=A\n(2,3,-2)=C\n(2,0,0)=V\n(-1,3,-2)=T\n(3,1,-1)=B\n(-1,1,-2)=O\n(2,3,0)=E",
        "벡터 명령\n시작 (0,0,0)\n+X 2\n+Y 3\n-Z 2\n-X 3\n-Y 2\n+Z 4",
    ], "영어 6글자", ["vector"], 3,
       "각 착지 좌표의 글자는 V,E,C,T,O,R이므로 VECTOR다.",
       ["vertex", "factor", "sector"],
       ["명령은 이전 좌표에 누적합니다.", "첫 세 착지는 (2,0,0),(2,3,0),(2,3,-2)입니다.", "착지 글자는 V-E-C-T-O-R입니다."]),
    puzzle("Y", "되돌아오는 길", "통로표에서 START→END의 유일한 길을 찾고, 같은 길로 귀환해 회문을 완성하세요.", [
        "양방향 통로\nSTART-R   R-O   R-E\nE-K       O-T   T-A\nT-I       I-N   A-V\nV-END\n\nK와 N에서는 더 이어지는 통로가 없습니다.",
    ], "영어 9글자 회문", ["rotavator"], 4,
       "유일한 전진 길은 R-O-T-A-V다. V를 중복하지 않은 귀환 A-T-O-R을 붙이면 ROTAVATOR다.",
       ["rotavvator", "rotator", "rotavatora"],
       ["R에서 E 쪽은 막다른 길입니다.", "전진 기록은 R-O-T-A-V입니다.", "귀환은 V를 빼고 A-T-O-R입니다."]),
    puzzle("Z", "아홉 칸의 역회전", "봉인 과정을 역순으로 적용하고 마지막에 덧붙인 두 글자를 제거하세요.", [
        {"layout": "GRID", "text": "암호판\nA L H\nB O R\nD S Y"},
        "봉인 과정\n1. 원문 뒤에 A·Z 추가\n2. 각 문자를 3-1-4 반복만큼 전진\n3. 3×3 격자를 시계 방향 90도 회전",
        {"layout": "GRID", "text": "역회전 확인\nH R Y    E Q U\nL O S →  I N O\nA B D    X A Z"},
    ], "영어 7글자", ["equinox"], 5,
       "격자를 반시계 방향으로 되돌린 HRY/LOS/ABD에서 3-1-4를 빼면 EQU/INO/XAZ다. 덧글자 A·Z를 제거하면 EQUINOX다.",
       ["equinoxaz", "equinoxe", "equinoxza"],
       ["가장 마지막 회전부터 반대로 되돌리세요.", "반시계 회전 결과는 HRY/LOS/ABD입니다.", "3-1-4를 빼고 마지막 A·Z를 제거하세요."]),
    puzzle("AA", "세 증언의 봉인", "범인은 한 명이며 네 증언 중 정확히 세 개만 참입니다. 범인의 두 자리 봉인을 거꾸로 입력하세요.", [
        "증언\n루나: 노아가 범인이다.\n노아: 테오는 범인이 아니다.\n세라: 루나는 범인이 아니다.\n테오: 노아의 증언은 거짓이다.",
        "봉인표\n루나=15  노아=47\n세라=62  테오=89\n\n범인의 봉인 숫자는 역순으로 입력합니다.",
    ], "숫자 2자리", ["74"], 5,
       "각 후보를 범인으로 가정하면 노아일 때만 루나·노아·세라의 증언이 참이고 테오의 증언만 거짓이다. 노아의 47을 뒤집어 74를 입력한다.",
       ["47", "26", "98"],
       ["후보 한 명씩 범인으로 가정해 참인 증언 수를 세세요.", "세 번째 증언까지 모두 참이 되는 후보를 찾으세요.", "해당 후보의 봉인 47을 마지막 규칙대로 뒤집으세요."]),
    puzzle("AB", "세 톱니의 자정", "100부터 999 사이의 금고 번호를 찾으세요. 세 톱니의 나머지 조건과 숫자합 조건을 모두 만족해야 합니다.", [
        "톱니 기록\n7로 나누면 나머지 2\n5로 나누면 나머지 3\n9로 나누면 나머지 4",
        "추가 봉인\n세 자리 숫자의 합은 13입니다.\n\n모든 조건을 만족하는 수는 하나뿐입니다.",
    ], "숫자 3자리", ["373"], 5,
       "첫 두 조건을 만족하는 수는 35마다 반복되고 세 조건을 합치면 315마다 반복된다. 세 자리 후보 373과 688 중 숫자합이 13인 373만 남는다.",
       ["58", "688", "733"],
       ["7과 5 조건을 함께 만족하는 가장 작은 수부터 35씩 더하세요.", "세 나머지 조건의 세 자리 후보는 두 개뿐입니다.", "후보 중 각 자리의 합이 13인 수를 고르세요."]),
    puzzle("AC", "거울 성도의 항해", "별을 밝기 1부터 7까지 정렬한 뒤, 거울 좌표를 복원해 문자판에서 일곱 글자를 읽으세요.", [
        "거울 규칙\n행은 그대로, 열은 1↔5, 2↔4, 3↔3으로 뒤집힙니다.\n\n관측 좌표는 (행, 거울에 보인 열)입니다.",
        "문자판\nQ E R T Y\nU I O P A\nS D C F G\nH J K L Z\nX V B N M",
        "별 기록\n도: 밝기5 (2,2)\n라: 밝기1 (1,4)\n마: 밝기7 (1,4)\n바: 밝기3 (4,2)\n사: 밝기6 (3,5)\n나: 밝기4 (2,4)\n다: 밝기2 (3,3)",
    ], "영어 7글자", ["eclipse"], 5,
       "밝기순 좌표를 실제 열로 뒤집으면 (1,2),(3,3),(4,4),(2,2),(2,4),(3,1),(1,2)이고 E-C-L-I-P-S-E가 된다.",
       ["ellipse", "clips", "ecpilse"],
       ["먼저 이름이 아니라 밝기 숫자로 별을 정렬하세요.", "각 좌표의 열을 6에서 빼면 실제 열입니다.", "복원 좌표의 글자는 E-C-L-I-P-S-E입니다."]),
    puzzle("AD", "여섯 등불의 역설", "켜짐을 1, 꺼짐을 0으로 하여 A부터 F까지 여섯 등불 상태를 순서대로 입력하세요.", [
        "연결 조건\nA와 B는 서로 다르다.\nB와 C는 같다.\nC와 D는 서로 다르다.\nD와 E는 서로 다르다.\nE와 F는 같다.",
        "마지막 조건\nA, D, F 중 정확히 두 개만 켜져 있습니다.",
    ], "0과 1로 이루어진 6자리", ["100100"], 5,
       "A를 켜짐으로 두면 B=0,C=0,D=1,E=0,F=0이고 마지막 조건도 만족한다. A를 끄면 A,D,F 중 하나만 켜져 탈락한다.",
       ["011011", "100110", "101100"],
       ["A의 상태를 두 경우로 나누고 연결 조건을 끝까지 전파하세요.", "A가 켜진 경우에는 B,C,E,F가 꺼지고 D가 켜집니다.", "A부터 F까지 켜짐/꺼짐을 숫자로 옮기세요."]),
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
    elif theme == "F":
        line(grid, 7, 7, 7, 13, "L")
        for x, z, color in [(7, 5, "Y"), (5, 7, "R"), (9, 7, "B"), (6, 9, "W"), (8, 9, "P")]:
            set_cell(grid, x, z, color)
        set_cell(grid, 7, 7, "G")
    elif theme == "G":
        line(grid, 9, 2, 5, 8, "Y"); line(grid, 5, 8, 9, 8, "Y"); line(grid, 9, 8, 5, 13, "Y")
    elif theme == "H":
        for x0 in (2, 8):
            line(grid, x0, 4, x0 + 4, 4, "W"); line(grid, x0, 4, x0, 9, "W")
            line(grid, x0 + 4, 4, x0 + 4, 9, "W"); line(grid, x0, 9, x0 + 3, 9, "W")
            line(grid, x0 + 3, 9, x0 + 4, 11, "P")
    elif theme == "I":
        cycle = [(7, 2), (10, 4), (11, 7), (9, 10), (6, 11), (3, 9), (3, 5)]
        for start, end in zip(cycle, cycle[1:] + cycle[:1]): line(grid, *start, *end, "C")
        for index, (x, z) in enumerate(cycle): set_cell(grid, x, z, "G" if index == 0 else "W")
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
    elif theme == "U":
        for z in (4, 8):
            for x in (3, 7, 11):
                for dx in range(-1, 2):
                    for dz in range(-1, 2):
                        if abs(dx) == 1 or abs(dz) == 1: set_cell(grid, x + dx, z + dz, "C")
        set_cell(grid, 3, 4, "G"); set_cell(grid, 7, 4, "G")
    elif theme == "V":
        line(grid, 2, 3, 7, 12, "R"); line(grid, 12, 3, 7, 12, "B")
        for x, z in [(4, 6), (10, 6), (7, 12)]: set_cell(grid, x, z, "W")
    elif theme == "W":
        for x in (3, 6, 9, 12): line(grid, x, 2, x, 12, "C")
        for z in (3, 6, 9, 12): line(grid, 2, z, 12, z, "C")
        for x, z in [(3, 3), (6, 6), (9, 9), (12, 3)]: set_cell(grid, x, z, "G")
    elif theme == "X":
        line(grid, 7, 7, 13, 7, "R"); line(grid, 7, 7, 7, 1, "B"); line(grid, 7, 7, 2, 12, "L")
        for x, z in [(7, 7), (13, 7), (7, 1), (2, 12)]: set_cell(grid, x, z, "G")
    elif theme == "Y":
        line(grid, 2, 2, 12, 2, "C"); line(grid, 12, 2, 12, 12, "C"); line(grid, 12, 12, 4, 12, "C"); line(grid, 4, 12, 4, 6, "C"); line(grid, 4, 6, 9, 6, "C")
        set_cell(grid, 2, 2, "L"); set_cell(grid, 9, 6, "R")
    elif theme == "Z":
        points = [(2, 9), (4, 4), (7, 2), (10, 5), (12, 10), (8, 12), (5, 9)]
        for a, b in zip(points, points[1:]): line(grid, *a, *b, "P")
        for x, z in points: set_cell(grid, x, z, "W")
    elif theme == "AA":
        for x, z, color in [(3, 3, "R"), (11, 3, "W"), (3, 11, "W"), (11, 11, "W")]:
            set_cell(grid, x, z, color)
        line(grid, 3, 3, 11, 11, "P"); line(grid, 11, 3, 3, 11, "P")
    elif theme == "AB":
        for radius, color in [(2, "G"), (4, "C"), (6, "P")]:
            for step in range(24):
                angle = 2 * math.pi * step / 24
                set_cell(grid, round(7 + radius * math.cos(angle)), round(7 + radius * math.sin(angle)), color)
        set_cell(grid, 7, 7, "R")
    elif theme == "AC":
        stars = [(2, 3), (5, 10), (7, 5), (9, 11), (12, 3), (11, 7), (4, 7)]
        for start, end in zip(stars, stars[1:]): line(grid, *start, *end, "B")
        for x, z in stars: set_cell(grid, x, z, "W")
        line(grid, 7, 1, 7, 13, "C")
    elif theme == "AD":
        for index, x in enumerate((2, 4, 6, 8, 10, 12)):
            set_cell(grid, x, 7, "G" if index in (0, 3) else "W")
            if index < 5: line(grid, x, 7, x + 2, 7, "P")
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


def make_room(level: str, index: int, source_index: int, definition: dict) -> dict:
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
        "inspiration": f"심야의 기록보관소 · {definition['title']}",
        "solutionExplanation": definition["explanation"],
        "wrongAnswerSamples": definition["wrong"],
    }
    design = device_design(level, sequence, center_x, minimum_z)
    if design is not None:
        terminal["question"] = design["pages"][0]
        terminal["pages"] = design["pages"]
        terminal["submissionMode"] = design["submissionMode"]
        terminal["requires"] = design["requires"]
    room = {
        "id": f"archive-{definition['letter'].lower()}",
        "sequence": sequence,
        "originalStage": source_index + 1,
        "title": definition["title"],
        "buildBounds": {"min": minimum, "max": maximum},
        "playBounds": {"min": minimum, "max": maximum},
        "spawn": position(center_x, 65, minimum_z + 4),
        "checkpoint": position(center_x, 65, minimum_z + 4),
        "visual": make_visual(center_x, minimum_z, thematic_pattern(source_index)),
        "completionMode": "ALL_MECHANICS",
        "mechanics": [terminal] + (design["mechanics"] if design is not None else []),
        "hints": [{"tier": tier, "text": text} for tier, text in enumerate(definition["hints"], 1)],
        "messages": {
            "intro": (f"문제: {terminal['question']}"
                      if design is not None
                      else "증거와 환경 장치를 조사한 뒤 /maze answer <정답>으로 제출하세요."),
            "completion": f"{definition['title']} 해독 완료.",
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
    if design is not None:
        room["structure"] = design["structure"]
    return room


MAZES = {
    "easy": {
        "mazeId": "midnight-easy",
        "mapVersion": "5.4.0-easy12",
        "displayName": "쉬움 미궁 · 잔잔한 기록실",
        "description": "1·2단계 퍼즐만 모은 입문용 미궁",
        "difficulties": {1, 2},
    },
    "normal": {
        "mazeId": "midnight-normal",
        "mapVersion": "5.1.0-normal12",
        "displayName": "보통 미궁 · 뒤엉킨 관측소",
        "description": "3·4단계 퍼즐만 모은 추론형 미궁",
        "difficulties": {3, 4},
    },
    "hard": {
        "mazeId": "midnight-hard",
        "mapVersion": "5.0.1-hard5",
        "displayName": "어려움 미궁 · 자정의 봉인실",
        "description": "5단계급 복합 추론 퍼즐 다섯 개로 구성된 최상급 미궁",
        "difficulties": {5},
    },
}


def selected_puzzles(level: str) -> list[tuple[int, dict]]:
    difficulties = MAZES[level]["difficulties"]
    return [(index, definition) for index, definition in enumerate(PUZZLES)
            if not definition.get("removed", False) and definition["difficulty"] in difficulties]


def build_pack(level: str) -> dict:
    assert len(PUZZLES) == 30
    metadata = MAZES[level]
    selected = selected_puzzles(level)
    rows = (len(selected) + 3) // 4
    return {
        "$schema": "https://mcpuzzle.dev/schema/map-pack.schema.json",
        "schemaVersion": 1,
        "mapVersion": metadata["mapVersion"],
        "mazeId": metadata["mazeId"],
        "displayName": metadata["displayName"],
        "description": metadata["description"],
        "locale": "ko-KR",
        "party": {"minPlayers": 1, "maxPlayers": 4},
        "world": {
            "mode": "GENERATED_VOID",
            "environment": "NORMAL",
            "bounds": {"min": position(-128, 48, -16), "max": position(128, 96, rows * 64)},
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
            "format": "심야의 미궁 난이도 컬렉션 30",
            "sha256": "5a43f214bba54335e2d39c07c61afc9fd844e00e48bcfbf2a438fed2cbb2cebb",
        },
        "rooms": [make_room(level, index, source_index, definition)
                  for index, (source_index, definition) in enumerate(selected)],
    }


def build_packs() -> dict[str, dict]:
    return {level: build_pack(level) for level in MAZES}


def build_design_document(packs: dict[str, dict]) -> str:
    labels = {
        "CLUE_REGIONS": "탐색 영역",
        "ORDERED_INPUT": "순서 입력",
        "CHOICE_INPUT": "후보 선택",
        "TOGGLE_INPUT": "토글 선택",
        "LOGIC_ANSWER": "텍스트 제출",
    }
    lines = [
        "# 환경 상호작용형 난이도 미궁 설계",
        "",
        "> 이 문서는 `generate_map.py`와 `interaction_design.py`에서 자동 생성됩니다. 직접 수정하지 마세요.",
        "",
        "- 대상: Paper 1.20.1 / Java 17 / 1–4인",
        "- 물리형 방은 `DEVICE_ONLY`, 혼합형 방은 환경 기믹 완료 후 `CHAT` 제출",
        "- 공통 조작권: 첫 입력자 10초, 유효 입력마다 갱신, 이탈·초기화·완료 시 해제",
        "- 오입력: 현재 입력 버퍼만 초기화하며 방 실패나 텔레포트를 일으키지 않음",
        "- 표지판: 생존 시야 높이, 양면 동일 발광 문구, 원형 장치는 중앙을 향하도록 배치",
        "",
    ]
    for level, heading in (("easy", "쉬움 미궁"), ("normal", "보통 미궁"), ("hard", "어려움 미궁")):
        pack = packs[level]
        lines.extend([
            f"## {heading}",
            "",
            f"맵 버전: `{pack['mapVersion']}`",
            "",
            "| 방 | 제목 | 입력 방식 | 제출 | 목표·조작법 |",
            "|---:|---|---|---|---|",
        ])
        for room in pack["rooms"]:
            terminal = room["mechanics"][0]
            device = room["mechanics"][1] if len(room["mechanics"]) > 1 else terminal
            mode = terminal.get("submissionMode", "CHAT")
            if terminal.get("requires"):
                mode += " (환경 완료 후)"
            instruction = terminal["question"].replace("|", "\\|").replace("\n", "<br>")
            lines.append(
                f"| {room['sequence']} | {room['title']} | {labels[device['type']]} | `{mode}` | {instruction} |"
            )
        lines.append("")
    lines.extend([
        "## 어려움 1번 복원 기준",
        "",
        "`ALH / BOR / DSY`를 반시계 방향으로 되돌리면 `HRY / LOS / ABD`, "
        "각 칸에서 `3-1-4` 반복값을 빼면 `EQU / INO / XAZ`가 된다. 마지막 덧글자 `A·Z`를 제거한 정답은 `EQUINOX`다.",
        "",
        "## 화면 검증 상태",
        "",
        "책 페이지는 PROSE/GRID 레이아웃과 사전 페이지 나눔을 사용한다. 실제 Paper 1.20.1 클라이언트 캡처를 수행하기 전까지 화면 검증은 미완료로 취급한다.",
        "",
    ])
    return "\n".join(lines)


def main() -> None:
    packs = build_packs()
    for level, pack in packs.items():
        destination = Path(__file__).with_name(f"{level}.jsonc")
        serialized = json.dumps(pack, ensure_ascii=False, indent=2) + "\n"
        destination.write_text(serialized, encoding="utf-8", newline="\n")
        print(f"wrote {destination} with {len(pack['rooms'])} rooms")
    design = Path(__file__).with_name("ROOM_DESIGN.md")
    design.write_text(build_design_document(packs), encoding="utf-8", newline="\n")
    print(f"wrote {design}")


if __name__ == "__main__":
    main()

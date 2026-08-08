from __future__ import annotations

import math


def position(x: int, y: int, z: int, yaw: float = 0.0) -> dict:
    return {"x": x, "y": y, "z": z, "yaw": yaw, "pitch": 0.0}


def block(x: int, y: int, z: int, material: str) -> dict:
    return {"position": position(x, y, z), "material": material}


def cuboid(x1: int, y1: int, z1: int, x2: int, y2: int, z2: int, material: str) -> dict:
    return {
        "bounds": {"min": position(x1, y1, z1), "max": position(x2, y2, z2)},
        "material": material,
    }


def sign(x: int, y: int, z: int, *lines: str, facing: str = "NORTH") -> dict:
    return {"position": position(x, y, z), "facing": facing, "lines": list(lines)}


def facing_toward(x: int, z: int, target_x: int, target_z: int) -> str:
    """Choose the cardinal sign plane that faces the intended ground-level viewer."""
    delta_x = target_x - x
    delta_z = target_z - z
    if abs(delta_x) > abs(delta_z):
        return "EAST" if delta_x > 0 else "WEST"
    return "SOUTH" if delta_z > 0 else "NORTH"


def control(control_id: str, token: str, label: str, activation: str,
            x: int, y: int, z: int, material: str = "POLISHED_BLACKSTONE",
            indicator: tuple[int, int, int] | None = None) -> dict:
    value = {
        "id": control_id,
        "token": token,
        "label": label,
        "activation": activation,
        "position": position(x, y, z),
        "material": material,
    }
    if indicator is not None:
        value["indicator"] = position(*indicator)
    return value


def expected(control_id: str, display: str | None = None) -> dict:
    value = {"control": control_id}
    if display is not None:
        value["display"] = display
    return value


def blueprint(title: str, center_x: int, minimum_z: int) -> dict:
    return {
        "blocks": [],
        "cuboids": [cuboid(center_x - 20, 65, minimum_z + 46,
                            center_x + 20, 70, minimum_z + 47, "POLISHED_BLACKSTONE_BRICKS")],
        "signs": [sign(center_x, 65, minimum_z + 8,
                       "[안내]", "장치를 관찰하고", "직접 조작하세요")],
    }


def problem_board(structure: dict, center_x: int, minimum_z: int,
                  panels: list[tuple[str, str, str, str]]) -> None:
    """Place three short, readable panels directly in the player's entrance sightline."""
    if len(panels) != 3:
        raise ValueError("problem board requires exactly three panels")
    structure["signs"][0]["lines"] = ["[안내]", "앞쪽 세 문제판", "부터 읽으세요"]
    for offset, lines in zip((-8, 0, 8), panels):
        x = center_x + offset
        z = minimum_z + 10
        structure["cuboids"].append(
            cuboid(x - 2, 64, z + 1, x + 2, 68, z + 1, "DARK_OAK_PLANKS")
        )
        structure["blocks"].append(block(x, 69, z + 1, "SEA_LANTERN"))
        structure["signs"].append(sign(x, 65, z, *lines))


def briefing(goal: str, observation: str, interaction: str) -> list[str]:
    return [f"목표\n{goal}\n\n관찰\n{observation}\n\n입력\n{interaction}"]


def button_bank(items: list[tuple[str, str, str]], center_x: int, minimum_z: int,
                structure: dict, row_size: int = 8, z_start: int = 32,
                material: str = "POLISHED_BLACKSTONE") -> list[dict]:
    controls = []
    for index, (control_id, token, label) in enumerate(items):
        row = index // row_size
        column = index % row_size
        columns = min(row_size, len(items) - row * row_size)
        x = center_x + column * 4 - (columns - 1) * 2
        z = minimum_z + z_start + row * 6
        controls.append(control(control_id, token, label, "CLICK", x, 67, z, material))
        structure["signs"].append(sign(x, 65, z, label))
    return controls


def ordered(mechanic_id: str, controls: list[dict], order: list[tuple[str, str]],
            result: str, groups: list[int] | None = None) -> dict:
    return {
        "id": mechanic_id,
        "type": "ORDERED_INPUT",
        "controls": controls,
        "expected": [expected(control_id, display) for control_id, display in order],
        "groups": groups or [],
        "operatorLockSeconds": 10,
        "resultText": result,
    }


def choice(mechanic_id: str, controls: list[dict], correct: str, result: str) -> dict:
    return {
        "id": mechanic_id,
        "type": "CHOICE_INPUT",
        "controls": controls,
        "correctControl": correct,
        "operatorLockSeconds": 10,
        "resultText": result,
    }


def result(structure: dict, mechanics: list[dict], pages: list[str],
           submission_mode: str = "DEVICE_ONLY", requires: list[str] | None = None) -> dict:
    return {
        "structure": structure,
        "mechanics": mechanics,
        "pages": pages,
        "submissionMode": submission_mode,
        "requires": requires or [],
    }


def device_design(level: str, sequence: int, center_x: int, minimum_z: int) -> dict | None:
    structure = blueprint("환경 장치를 조작하세요", center_x, minimum_z)

    if level == "easy" and sequence == 1:
        problem_board(structure, center_x, minimum_z, [
            ("[문제]", "두 전력의 합을", "13자리 이진수로", "변환하라"),
            ("[관찰]", "청록 발전기", "금빛 발전기", "자리값 패널"),
            ("[입력]", "켜짐은 1", "꺼짐은 0", "완성 후 제출"),
        ])
        structure["cuboids"].extend([
            cuboid(center_x - 14, 64, minimum_z + 18, center_x - 8, 69,
                   minimum_z + 22, "CYAN_CONCRETE"),
            cuboid(center_x + 8, 64, minimum_z + 18, center_x + 14, 69,
                   minimum_z + 22, "GOLD_BLOCK"),
        ])
        structure["signs"].extend([
            sign(center_x - 11, 65, minimum_z + 17, "청록 발전기", "출력 4096"),
            sign(center_x + 11, 65, minimum_z + 17, "금빛 발전기", "출력 4"),
            sign(center_x, 65, minimum_z + 29, "[자리값]", "왼쪽 2¹²", "오른쪽 2⁰", "13자리 모두 사용"),
        ])
        items = [(f"bit-{power}", "1", f"2^{power}") for power in range(12, -1, -1)]
        controls = []
        for index, (control_id, token, label) in enumerate(items):
            x = center_x - 18 + index * 3
            z = minimum_z + 38
            item = control(control_id, token, label, "CLICK", x, 67, z)
            item["indicator"] = position(item["position"]["x"], 69, item["position"]["z"] + 1)
            controls.append(item)
            structure["signs"].append(sign(x, 65, z, label))
        structure["signs"].extend([
            sign(center_x - 3, 65, minimum_z + 44, "제출"),
            sign(center_x + 3, 65, minimum_z + 44, "초기화"),
        ])
        mechanic = {
            "id": "binary-toggles", "type": "TOGGLE_INPUT", "controls": controls,
            "expectedActive": ["bit-12", "bit-2"], "maxSelections": 13,
            "operatorLockSeconds": 10,
            "submitButton": position(center_x - 3, 67, minimum_z + 44),
            "clearButton": position(center_x + 3, 67, minimum_z + 44),
            "resultText": "1000000000100",
        }
        return result(structure, [mechanic],
                      briefing("두 발전기의 출력값을 더하고, 그 합을 2¹²부터 2⁰까지 빠짐없는 13자리 이진수로 바꾸세요.",
                               "방 안의 두 발전기 출력과 비트 자리값 패널을 확인하세요.",
                               "각 자리 버튼을 켜짐=1, 꺼짐=0으로 맞춘 뒤 제출 버튼을 누르세요."))

    if level == "easy" and sequence == 2:
        problem_board(structure, center_x, minimum_z, [
            ("[문제]", "일곱 기록을", "무지개 순서로", "복원하라"),
            ("[관찰]", "색 띠는 섞임", "각 기록의", "마지막 글자"),
            ("[입력]", "일곱 띠 조사", "네 글자 성어", "채팅 제출"),
        ])
        colors = [
            ("blue", "파랑", "LIGHT_BLUE_CONCRETE", "[파랑] 정원입"),
            ("red", "빨강", "RED_CONCRETE", "[빨강] 이야기주"),
            ("purple", "보라", "PURPLE_CONCRETE", "[보라] 은하다"),
            ("green", "초록", "GREEN_CONCRETE", "[초록] 의문독"),
            ("orange", "주황", "ORANGE_CONCRETE", "[주황] 문명경"),
            ("indigo", "남색", "BLUE_CONCRETE", "[남색] 답변니"),
            ("yellow", "노랑", "YELLOW_CONCRETE", "[노랑] 제목야"),
        ]
        regions = []
        for index, (region_id, label, material, message) in enumerate(colors):
            z = minimum_z + 13 + index * 4
            structure["cuboids"].append(cuboid(center_x - 15, 64, z, center_x + 15, 64, z + 1, material))
            structure["signs"].append(sign(center_x - 18, 65, z, label, facing="EAST"))
            regions.append({
                "id": region_id,
                "bounds": {"min": position(center_x - 15, 64, z), "max": position(center_x + 15, 67, z + 1)},
                "message": message,
                "sound": "BLOCK_AMETHYST_BLOCK_CHIME",
                "pitch": 0.7 + index * 0.1,
            })
        mechanic = {"id": "seven-records", "type": "CLUE_REGIONS", "regions": regions}
        return result(structure, [mechanic],
                      briefing("흩어진 일곱 기록을 무지개색 순서로 복원해 숨은 네 글자 성어를 찾으세요.",
                               "색 띠는 순서가 섞여 있습니다. 각 띠에서 발견한 기록의 마지막 글자를 적어 두세요.",
                               "일곱 띠를 모두 조사한 뒤 완성 문장의 앞 네 글자를 채팅으로 제출하세요."),
                      "CHAT", ["seven-records"])

    if level == "easy" and sequence == 3:
        problem_board(structure, center_x, minimum_z, [
            ("[문제]", "하늘의 왕은", "누구인가?", "한 명 선택"),
            ("[관찰]", "하늘 / 번개", "독수리", "신들의 왕"),
            ("[입력]", "전시 4곳 조사", "후보 아래", "버튼 클릭"),
        ])
        candidates = [
            ("poseidon", "포세이돈", ["영역: 바다", "무기: 삼지창", "상징: 말", "지위: 바다의 왕"], "PRISMARINE_BRICKS"),
            ("athena", "아테나", ["영역: 지혜", "무기: 창", "상징: 올빼미", "지위: 여신"], "QUARTZ_PILLAR"),
            ("zeus", "제우스", ["영역: 하늘", "무기: 번개", "상징: 독수리", "지위: 신들의 왕"], "YELLOW_CONCRETE"),
            ("hades", "하데스", ["영역: 명계", "무기: 이지창", "상징: 케르베로스", "지위: 명계의 왕"], "POLISHED_BLACKSTONE_BRICKS"),
        ]
        controls = []
        for index, (candidate_id, label, lines, display_material) in enumerate(candidates):
            x = center_x - 15 + index * 10
            structure["cuboids"].append(cuboid(x - 3, 64, minimum_z + 27, x + 3, 66,
                                                 minimum_z + 31, display_material))
            structure["signs"].append(sign(x, 67, minimum_z + 29, label, *lines[:3]))
            structure["signs"].append(sign(x, 65, minimum_z + 35, lines[3]))
            controls.append(control(candidate_id, label, label, "CLICK", x, 67,
                                    minimum_z + 38, "CHISELED_QUARTZ_BLOCK"))
        return result(structure, [choice("guardian-choice", controls, "zeus", "제우스")],
                      briefing("하늘을 다스리고 번개를 들며, 독수리를 상징으로 삼는 신들의 왕을 찾으세요.",
                               "네 후보 전시대에 영역·무기·상징·지위가 따로 기록되어 있습니다.",
                               "네 조건을 모두 만족하는 후보 아래의 버튼을 누르세요."))

    if level == "easy" and sequence == 4:
        problem_board(structure, center_x, minimum_z, [
            ("[문제]", "세 단어 쌍의", "공통 변화를", "FUN에 적용"),
            ("[관찰]", "바뀐 위치와", "바뀐 글자를", "서로 비교"),
            ("[입력]", "FUN의 첫 글자", "대신 올 글자", "버튼 선택"),
        ])
        structure["signs"].extend([
            sign(center_x - 12, 65, minimum_z + 20, "보기 1", "FEAR → PEAR"),
            sign(center_x - 4, 65, minimum_z + 20, "보기 2", "FIG → PIG"),
            sign(center_x + 4, 65, minimum_z + 20, "보기 3", "FACE → PACE"),
            sign(center_x + 12, 65, minimum_z + 20, "목표", "FUN → ?"),
        ])
        controls = button_bank([("f", "F", "F 유지"), ("p", "P", "P 교체"), ("b", "B", "B 교체")],
                               center_x, minimum_z, structure, row_size=3, z_start=36)
        return result(structure, [choice("letter-choice", controls, "p", "PUN")],
                      briefing("세 영어 단어 쌍에 공통으로 적용된 한 글자 변화를 찾아 FUN에 적용하세요.",
                               "보기 세 개에서 어느 위치의 글자가 무엇으로 바뀌었는지 비교하세요.",
                               "FUN의 첫 글자 자리에 들어갈 글자 버튼을 누르세요."))

    if level == "easy" and sequence == 5:
        problem_board(structure, center_x, minimum_z, [
            ("[문제]", "현재 패의", "가장 높은 족보를", "판정하라"),
            ("[관찰]", "다섯 카드의", "같은 숫자 수와", "족보표 비교"),
            ("[입력]", "족보를 만드는", "핵심 카드 선택", "판정 버튼"),
        ])
        structure["signs"].extend([
            sign(center_x - 7, 65, minimum_z + 20, "[족보표 상]", "포카드=같은 수4", "트리플=같은 수3"),
            sign(center_x + 7, 65, minimum_z + 20, "[족보표 하]", "투페어=쌍2", "원페어=쌍1", "하이카드=쌍0"),
        ])
        cards = [("five-hearts", "5", "♥ 5"), ("three-spades", "3", "♠ 3"),
                 ("five-clubs", "5", "♣ 5"), ("nine-diamonds", "9", "♦ 9"),
                 ("king-hearts", "K", "♥ K")]
        controls = button_bank(cards, center_x, minimum_z, structure, row_size=5, z_start=34)
        for item in controls:
            item["indicator"] = position(item["position"]["x"], 69, item["position"]["z"] + 1)
        structure["signs"].extend([
            sign(center_x - 3, 65, minimum_z + 42, "족보 판정"),
            sign(center_x + 3, 65, minimum_z + 42, "선택 초기화"),
        ])
        mechanic = {
            "id": "pair-cards", "type": "TOGGLE_INPUT", "controls": controls,
            "expectedActive": ["five-hearts", "five-clubs"], "maxSelections": 2,
            "operatorLockSeconds": 10,
            "submitButton": position(center_x - 3, 67, minimum_z + 42),
            "clearButton": position(center_x + 3, 67, minimum_z + 42),
            "resultText": "원페어",
        }
        return result(structure, [mechanic],
                      briefing("다섯 장의 카드가 만드는 가장 높은 포커 족보를 판정하세요.",
                               "카드 전시대에서 같은 숫자가 몇 장씩 있는지 세고, 방 안의 족보표와 비교하세요.",
                               "그 족보를 만드는 핵심 카드만 선택한 뒤 족보 판정 버튼을 누르세요."))

    if level == "easy" and sequence == 6:
        problem_board(structure, center_x, minimum_z, [
            ("[문제]", "기사의 규칙으로", "금빛 봉인 5개", "모두 깨워라"),
            ("[관찰]", "직선으로 2칸", "옆으로 1칸", "L자 이동"),
            ("[입력]", "START부터", "금빛 발판만", "차례로 밟기"),
        ])
        controls = []
        files = "ABCDE"
        route = ["b1", "d2", "e4", "c5", "a4"]
        for rank in range(1, 6):
            for file_index, file_name in enumerate(files):
                square = f"{file_name}{rank}"
                square_id = square.lower()
                material = ("GOLD_BLOCK" if square_id in route else
                            "WHITE_CONCRETE" if (rank + file_index) % 2 == 0 else "GRAY_CONCRETE")
                controls.append(control(square.lower(), square, square, "STEP",
                                        center_x - 8 + file_index * 4, 65,
                                        minimum_z + 16 + (rank - 1) * 5, material))
        structure["signs"].append(sign(center_x - 4, 65, minimum_z + 13,
                                       "START", "첫 금빛 봉인"))
        heart_rows = {
            70: (-3, -2, 2, 3),
            69: tuple(range(-4, 5)),
            68: tuple(range(-3, 4)),
            67: tuple(range(-2, 3)),
            66: (-1, 0, 1),
            65: (0,),
        }
        for y, offsets in heart_rows.items():
            for offset in offsets:
                structure["blocks"].append(block(center_x + offset, y, minimum_z + 45,
                                                   "RED_CONCRETE"))
        mechanic = ordered("heart-route", controls,
                           [(square, letter) for square, letter in zip(route, "HEART")], "HEART")
        return result(structure, [mechanic],
                      briefing("START에서 출발해 금빛 봉인 다섯 개를 기사의 이동만으로 하나의 길로 이으세요.",
                               "기사는 직선으로 두 칸 간 뒤 옆으로 한 칸 꺾는 L자 모양으로 이동합니다.",
                               "금빛 발판만 차례로 밟으세요. 착지 글자는 액션바에 드러나며 오입력은 현재 경로만 지웁니다."))

    if level == "easy" and sequence == 7:
        problem_board(structure, center_x, minimum_z, [
            ("[문제]", "찢어진 조각의", "원래 국기를", "I~IV에서 찾기"),
            ("[관찰]", "조각 제단과", "네 국기의", "색·띠 방향"),
            ("[입력]", "국가명은 없음", "번호로 비교", "후보 버튼"),
        ])
        structure["cuboids"].extend([
            cuboid(center_x - 11, 64, minimum_z + 18, center_x - 3, 67,
                   minimum_z + 21, "BLUE_CONCRETE"),
            cuboid(center_x + 3, 64, minimum_z + 18, center_x + 11, 67,
                   minimum_z + 21, "WHITE_CONCRETE"),
        ])
        structure["signs"].extend([
            sign(center_x - 7, 65, minimum_z + 17, "바탕 조각", "파랑 4개"),
            sign(center_x + 7, 65, minimum_z + 17, "띠 조각", "흰색 2개", "모두 대각선"),
        ])
        flags = [("finland", "I", "WHITE_CONCRETE"), ("jamaica", "II", "GREEN_CONCRETE"),
                 ("scotland", "III", "BLUE_CONCRETE"), ("sweden", "IV", "BLUE_CONCRETE")]
        controls = []
        for index, (flag_id, label, base) in enumerate(flags):
            x = center_x - 15 + index * 10
            structure["cuboids"].append(cuboid(x - 3, 66, minimum_z + 28, x + 3, 70, minimum_z + 28, base))
            if flag_id == "scotland":
                for delta in range(-2, 3):
                    structure["blocks"].append(block(x + delta, 68 + delta // 2, minimum_z + 27, "WHITE_CONCRETE"))
                    structure["blocks"].append(block(x + delta, 68 - delta // 2, minimum_z + 27, "WHITE_CONCRETE"))
            elif flag_id == "finland":
                structure["cuboids"].append(cuboid(x - 3, 68, minimum_z + 27, x + 3, 68, minimum_z + 27, "BLUE_CONCRETE"))
                structure["cuboids"].append(cuboid(x - 1, 66, minimum_z + 27, x - 1, 70, minimum_z + 27, "BLUE_CONCRETE"))
            elif flag_id == "sweden":
                structure["cuboids"].append(cuboid(x - 3, 68, minimum_z + 27, x + 3, 68, minimum_z + 27, "YELLOW_CONCRETE"))
                structure["cuboids"].append(cuboid(x - 1, 66, minimum_z + 27, x - 1, 70, minimum_z + 27, "YELLOW_CONCRETE"))
            else:
                structure["cuboids"].append(cuboid(x - 3, 68, minimum_z + 27, x + 3, 68, minimum_z + 27, "YELLOW_CONCRETE"))
            structure["signs"].append(sign(x, 65, minimum_z + 31, f"후보 {label}"))
            controls.append(control(flag_id, label, f"후보 {label}", "CLICK", x, 67,
                                    minimum_z + 35, "SMOOTH_STONE"))
        return result(structure, [choice("flag-choice", controls, "scotland", "스코틀랜드")],
                      briefing("찢어진 여섯 조각이 원래 만들었던 국기를 이름 없는 후보 네 개에서 찾으세요.",
                               "조각 제단에서 바탕색·띠 색·띠 방향을 확인하고 후보 I~IV의 문양과 비교하세요.",
                               "모든 조각 조건을 만족하는 후보 번호 아래의 버튼을 누르세요."))

    if level == "easy" and sequence == 8:
        problem_board(structure, center_x, minimum_z, [
            ("[문제]", "네 시계 좌표로", "장소 단어를", "완성하라"),
            ("[관찰]", "짧은 바늘 행", "빛난 긴 바늘 열", "뒤 문자판"),
            ("[입력]", "시계 1~4 순서", "빛난 열의 값", "버튼 선택"),
        ])
        values = (0, 12, 24, 36, 48)
        short_hands = (1, 2, 4, 5)
        long_hands = (24, 36, 48, 48)
        items = [(f"clock-{stage}-{value}", str(value), str(value))
                 for stage in range(1, 5) for value in values]
        controls = button_bank(items, center_x, minimum_z, structure, row_size=5, z_start=20)
        structure["signs"].append(sign(center_x, 65, minimum_z + 14,
                                       "긴 바늘 열", "0 12 24 36 48", "빛난 칸 확인"))
        for stage, (short_hand, long_hand) in enumerate(zip(short_hands, long_hands), 1):
            row_z = minimum_z + 20 + (stage - 1) * 6
            structure["signs"].append(sign(center_x - 14, 65, row_z,
                                           f"[시계 {stage}]", f"짧은 바늘 {short_hand}시",
                                           facing="EAST"))
            for column, value in enumerate(values):
                x = center_x - 8 + column * 4
                material = "SEA_LANTERN" if value == long_hand else "BLACK_CONCRETE"
                structure["blocks"].append(block(x, 64, row_z - 3, material))
        structure["signs"].extend([
            sign(center_x - 15, 65, minimum_z + 43, "[문자판]", "긴 0 12 24", "36 48"),
            sign(center_x - 9, 65, minimum_z + 43, "1시 행", "A B C D E"),
            sign(center_x - 3, 65, minimum_z + 43, "2시 행", "F G H I J"),
            sign(center_x + 3, 65, minimum_z + 43, "3시 행", "K L M N O"),
            sign(center_x + 9, 65, minimum_z + 43, "4시 행", "P Q R S T"),
            sign(center_x + 15, 65, minimum_z + 43, "5시 행", "U V W X Y"),
        ])
        order = [("clock-1-24", "C"), ("clock-2-36", "I"),
                 ("clock-3-48", "T"), ("clock-4-48", "Y")]
        return result(structure, [ordered("clock-columns", controls, order, "CITY")],
                      briefing("네 시계가 가리키는 좌표를 문자판에서 읽어 장소를 뜻하는 네 글자 단어를 만드세요.",
                               "각 장치의 짧은 바늘은 행이고, 다섯 긴 바늘 칸 중 빛난 칸은 열입니다. 뒤쪽 문자판에서 교차 글자를 확인하세요.",
                               "시계 1부터 4까지 빛난 긴 바늘 열의 값 버튼을 차례로 누르세요."))

    if level == "easy" and sequence == 9:
        problem_board(structure, center_x, minimum_z, [
            ("[문제]", "물 분자를", "원자번호 3자리로", "압축하라"),
            ("[관찰]", "분자 모형·화학식", "변환 예시", "원소 기둥"),
            ("[입력]", "원자를 펼친 뒤", "번호 대신", "원소 버튼"),
        ])
        structure["signs"].extend([
            sign(center_x, 65, minimum_z + 18, "물 분자", "H₂O"),
            sign(center_x - 10, 65, minimum_z + 27, "[변환 예시]", "CO₂ → C,O,O", "6,8,8 → 688"),
            sign(center_x + 10, 65, minimum_z + 27, "[규칙]", "아래첨자=개수", "번호를 이어 붙임"),
        ])
        structure["blocks"].extend([
            block(center_x - 4, 65, minimum_z + 23, "WHITE_CONCRETE"),
            block(center_x, 65, minimum_z + 23, "RED_CONCRETE"),
            block(center_x + 4, 65, minimum_z + 23, "WHITE_CONCRETE"),
        ])
        controls = button_bank([("h", "H", "H · 1"), ("o", "O", "O · 8"),
                                 ("c", "C", "C · 6"), ("n", "N", "N · 7")],
                               center_x, minimum_z, structure, row_size=4, z_start=36)
        mechanic = ordered("water-elements", controls,
                           [("h", "1"), ("h", "1"), ("o", "8")], "1·1·8")
        return result(structure, [mechanic],
                      briefing("물 분자의 화학식을 원자 단위로 펼친 뒤 각 원자를 원자번호로 바꿔 세 자리 수를 만드세요.",
                               "물 분자 모형과 H₂O 표지, CO₂ 변환 예시, 네 원소 기둥의 원자번호를 함께 확인하세요.",
                               "펼친 원자 순서대로 해당 원소 버튼을 누르세요. 액션바에는 원자번호가 기록됩니다."))

    if level == "easy" and sequence == 10:
        problem_board(structure, center_x, minimum_z, [
            ("[문제]", "세 수를 각각", "로마 숫자로", "바꿔 이어라"),
            ("[관찰]", "기호값 패널", "101 / 6 / 50", "묶음은 독립"),
            ("[입력]", "왼쪽 묶음부터", "로마 문자", "차례로 버튼"),
        ])
        structure["signs"].extend([
            sign(center_x - 12, 65, minimum_z + 18, "[기호값]", "I=1  V=5", "X=10"),
            sign(center_x + 12, 65, minimum_z + 18, "[기호값]", "L=50", "C=100"),
            sign(center_x - 10, 65, minimum_z + 26, "첫째 묶음", "101"),
            sign(center_x, 65, minimum_z + 26, "둘째 묶음", "6"),
            sign(center_x + 10, 65, minimum_z + 26, "셋째 묶음", "50"),
        ])
        controls = button_bank([(letter.lower(), letter, letter) for letter in "CIVLX"],
                               center_x, minimum_z, structure, row_size=5, z_start=36)
        mechanic = ordered("roman-entry", controls,
                           [(letter.lower(), letter) for letter in "CIVIL"], "CIVIL")
        return result(structure, [mechanic],
                      briefing("101, 6, 50을 각각 로마 숫자로 바꾸고 세 변환 결과를 왼쪽부터 이어 붙이세요.",
                               "방 안의 로마 숫자 기호값 패널과 서로 독립된 세 수의 제단을 확인하세요.",
                               "각 묶음의 로마 문자를 생략하지 말고 왼쪽 묶음부터 차례로 누르세요."))

    if level == "easy" and sequence == 11:
        problem_board(structure, center_x, minimum_z, [
            ("[문제]", "대상 기록과 맞는", "방어 건물을", "한 개 찾기"),
            ("[관찰]", "이동·수리 불가", "수송 불가", "적 공격 가능"),
            ("[입력]", "명령 패널 비교", "조건 모두 확인", "후보 버튼"),
        ])
        structure["signs"].extend([
            sign(center_x - 8, 65, minimum_z + 19, "[대상 기록]", "스스로 이동 불가", "적 공격 가능"),
            sign(center_x + 8, 65, minimum_z + 19, "[제외 기능]", "수리·채집 없음", "적재·하역 없음"),
        ])
        candidates = [("marine", "마린", ("이동·정지", "공격·순찰")),
                      ("scv", "SCV", ("이동·정지·공격", "수리·채집")),
                      ("bunker", "벙커", ("적재·하역", "집결")),
                      ("turret", "미사일 터렛", ("정지·공격",))]
        controls = []
        for index, (candidate_id, label, command_lines) in enumerate(candidates):
            x = center_x - 15 + index * 10
            structure["cuboids"].append(cuboid(x - 3, 64, minimum_z + 27, x + 3, 66, minimum_z + 31, "IRON_BLOCK"))
            structure["signs"].append(sign(x, 67, minimum_z + 29, label, *command_lines))
            controls.append(control(candidate_id, label, label, "CLICK", x, 67, minimum_z + 37, "IRON_BLOCK"))
        return result(structure, [choice("unit-choice", controls, "turret", "미사일 터렛")],
                      briefing("스스로 이동하지 못하며 적을 공격하지만 수리·채집·수송 기능은 없는 방어 건물을 찾으세요.",
                               "대상 기록의 기능 조건과 네 후보 전시대의 실제 명령 패널을 비교하세요.",
                               "조건을 모두 만족하는 후보 아래의 버튼을 누르세요."))

    if level == "easy" and sequence == 12:
        problem_board(structure, center_x, minimum_z, [
            ("[문제]", "이 장소를 뜻하는", "두 음절을", "조립하라"),
            ("[관찰]", "초성·중성·종성", "같은 번호끼리", "1번 뒤 2번"),
            ("[입력]", "초→중→종", "받침 없음도", "버튼으로 입력"),
        ])
        structure["signs"].extend([
            sign(center_x - 10, 65, minimum_z + 19, "[초성함]", "1 = ㅁ", "2 = ㄱ"),
            sign(center_x, 65, minimum_z + 19, "[중성함]", "1 = ㅣ", "2 = ㅜ"),
            sign(center_x + 10, 65, minimum_z + 19, "[종성함]", "1 = 없음", "2 = ㅇ"),
            sign(center_x, 65, minimum_z + 25, "[조립 규칙]", "같은 번호끼리", "1번 뒤 2번"),
        ])
        labels = [("m", "ㅁ", "ㅁ"), ("i", "ㅣ", "ㅣ"), ("none", "없음", "받침 없음"),
                  ("g", "ㄱ", "ㄱ"), ("u", "ㅜ", "ㅜ"), ("ng", "ㅇ", "ㅇ"),
                  ("n", "ㄴ", "ㄴ"), ("a", "ㅏ", "ㅏ"), ("r", "ㄹ", "ㄹ")]
        controls = button_bank(labels, center_x, minimum_z, structure, row_size=5, z_start=30)
        order = [("m", "ㅁ"), ("i", "ㅣ"), ("none", "없음"),
                 ("g", "ㄱ"), ("u", "ㅜ"), ("ng", "ㅇ")]
        return result(structure, [ordered("hangul-assembly", controls, order, "미궁", [3, 3])],
                      briefing("세 보관함에서 같은 번호의 자모를 모아 이 장소를 뜻하는 두 음절을 만드세요.",
                               "초성·중성·종성 보관함의 1번끼리, 2번끼리 묶고 1번 음절 뒤에 2번 음절을 놓으세요.",
                               "각 음절을 초성→중성→종성 순서로 누르세요. 받침이 없을 때도 전용 버튼을 입력합니다."))

    if level == "normal" and sequence == 1:
        items = [(f"tile-{index}-{letter.lower()}", letter, letter)
                 for index, letter in enumerate("SPELLER", 1)]
        items.extend([("tile-a", "A", "A"), ("tile-t", "T", "T")])
        controls = button_bank(items, center_x, minimum_z, structure, row_size=9, z_start=36)
        order = [(f"tile-{index}-{letter.lower()}", letter)
                 for index, letter in enumerate("SPELLER", 1)]
        return result(structure, [ordered("speller-slots", controls, order, "SPELLER")],
                      ["남은 문자 타일을 빈 슬롯에 SPELLER가 되도록 왼쪽부터 순서대로 꽂으세요."])

    if level == "normal" and sequence == 2:
        controls = button_bank([("dot", ".", "점 ·"), ("dash", "-", "선 —")],
                               center_x, minimum_z, structure, row_size=2, z_start=36)
        order = [("dot" if symbol == "." else "dash", symbol) for symbol in ".-..---...-."]
        mechanic = ordered("love-signal", controls, order, "LOVE", [4, 3, 4, 1])
        return result(structure, [mechanic],
                      ["점과 선 장치로 LOVE의 모스 신호를 입력해 네 글자를 점등하세요. 그 뒤 연상되는 제목을 채팅으로 제출하세요."],
                      "CHAT", ["love-signal"])

    if level == "normal" and sequence == 3:
        controls = []
        for row in range(1, 7):
            for column in range(1, 7):
                cell = f"c{column}-{row}"
                controls.append(control(cell, f"{column},{row}", f"({column},{row})", "STEP",
                                        center_x - 10 + (column - 1) * 4, 65,
                                        minimum_z + 14 + (row - 1) * 5, "LIGHT_BLUE_CONCRETE"))
        coords = [(1, 4), (2, 2), (3, 6), (4, 1), (5, 3), (6, 5)]
        order = [(f"c{x}-{z}", letter) for (x, z), letter in zip(coords, "FLOWER")]
        structure["signs"].append(sign(center_x, 65, minimum_z + 44, "열 1 2 3 4 5 6"))
        return result(structure, [ordered("flower-grid", controls, order, "FLOWER")],
                      ["6×6 격자에서 기록된 여섯 좌표를 순서대로 밟으세요. 좌표는 (열,행)입니다."])

    if level == "normal" and sequence == 4:
        values = [1, 2, 4, 5, 7, 8]
        controls = []
        for index, value in enumerate(values):
            angle = 2 * math.pi * index / len(values)
            x = round(center_x + 11 * math.cos(angle))
            z = round(minimum_z + 27 + 11 * math.sin(angle))
            controls.append(control(f"number-{value}", str(value), str(value), "STEP",
                                    x, 65, z, "ORANGE_CONCRETE"))
            sign_z = z + (2 if z < minimum_z + 27 else -2)
            structure["signs"].append(sign(
                x, 65, sign_z, str(value),
                facing=facing_toward(x, sign_z, center_x, minimum_z + 27)
            ))
        order = [(f"number-{value}", str(value)) for value in (8, 5, 7, 1, 4, 2)]
        return result(structure, [ordered("number-orbit", controls, order, "857142")],
                      ["원형 숫자 궤도에서 규칙에 맞는 여섯 숫자를 순서대로 밟으세요."])

    if level == "normal" and sequence == 5:
        structure["signs"].extend([
            sign(center_x - 12 + index * 6, 65, minimum_z + 20, f"패널 {index + 1}")
            for index in range(5)
        ])
        controls = button_bank([(letter.lower(), letter, letter) for letter in "SOUNDABCE"],
                               center_x, minimum_z, structure, row_size=10, z_start=36)
        return result(structure, [ordered("segment-entry", controls,
                                          [(letter.lower(), letter) for letter in "SOUND"], "SOUND")],
                      ["다섯 일곱 선분 패널의 모양을 읽고 대응하는 영문자를 차례대로 누르세요."])

    if level == "normal" and sequence == 6:
        clues = [("feed-f", "F 음", "첫 장치: F", 0.7),
                 ("feed-eed", "EED 음", "둘째 장치: EED", 0.9),
                 ("banana-ba", "BA", "셋째 장치: BA", 1.0),
                 ("banana-na1", "NA", "넷째 장치: NA", 1.2),
                 ("banana-na2", "NA", "다섯째 장치: NA", 1.4)]
        regions = []
        for index, (region_id, label, message, pitch) in enumerate(clues):
            x = center_x - 12 + index * 6
            z = minimum_z + 28
            structure["blocks"].append(block(x, 64, z, "NOTE_BLOCK"))
            structure["signs"].append(sign(x, 65, z + 2, label))
            regions.append({
                "id": region_id,
                "bounds": {"min": position(x - 1, 64, z - 1), "max": position(x + 1, 67, z + 1)},
                "message": message,
                "sound": "BLOCK_NOTE_BLOCK_HARP",
                "pitch": pitch,
            })
        mechanic = {"id": "food-sounds", "type": "CLUE_REGIONS", "regions": regions}
        return result(structure, [mechanic],
                      ["다섯 음표·음절 발판을 모두 직접 밟아 FEED와 BA-NA-NA를 확인한 뒤 먹이를 떠올려 동물 이름을 채팅으로 제출하세요."],
                      "CHAT", ["food-sounds"])

    if level == "normal" and sequence == 7:
        letters = "QWERTYUIOP"
        controls = [control(f"key-{letter.lower()}", letter, letter, "STEP",
                            center_x - 18 + index * 4, 65, minimum_z + 28, "WHITE_CONCRETE")
                    for index, letter in enumerate(letters)]
        structure["signs"].append(sign(center_x, 65, minimum_z + 34,
                                       "Q W E R T", "Y U I O P"))
        mechanic = ordered("keyboard-route", controls,
                           [(f"key-{letter.lower()}", letter) for letter in "QWERTY"], "QWERTY")
        return result(structure, [mechanic],
                      ["대형 QWERTY 바닥에서 제시된 각 글자의 바로 왼쪽 키를 이어서 밟으세요."])

    if level == "normal" and sequence == 8:
        structure["cuboids"].append(cuboid(center_x - 8, 65, minimum_z + 22,
                                            center_x + 8, 70, minimum_z + 28, "BLUE_ICE"))
        structure["signs"].append(sign(center_x, 65, minimum_z + 19, "여덟 번째 행성"))
        items = [("sea", "海", "海"), ("king", "王", "王"), ("star", "星", "星"),
                 ("sky", "天", "天"), ("earth", "地", "地"), ("sun", "日", "日")]
        controls = button_bank(items, center_x, minimum_z, structure, row_size=6, z_start=36)
        return result(structure, [ordered("planet-seals", controls,
                                          [("sea", "海"), ("king", "王"), ("star", "星")], "海王星")],
                      ["여덟 번째 행성 전시를 보고 이름을 이루는 세 한자 봉인을 순서대로 여세요."])

    if level == "normal" and sequence == 9:
        glyphs = ["자", "축", "인", "묘", "진", "사", "오", "미", "신", "유", "술", "해",
                  "쥐", "소", "호랑이", "토끼", "용", "뱀", "말", "양", "원숭이", "닭", "개", "돼지"]
        controls = button_bank([(f"altar-{index}", label, label) for index, label in enumerate(glyphs)],
                               center_x, minimum_z, structure, row_size=8, z_start=26)
        return result(structure, [ordered("zodiac-altars", controls,
                                          [("altar-1", "축"), ("altar-13", "소")], "축소")],
                      ["열두 지지 글자와 동물 제단을 비교하고 같은 자리를 뜻하는 글자와 동물을 차례로 고르세요."])

    if level == "normal" and sequence == 10:
        matrix = [["M", "A", "R", "T"], ["O", "I", "B", "C"], ["D", "E", "S", "F"]]
        controls = []
        for row, letters in enumerate(matrix):
            for column, letter in enumerate(letters):
                controls.append(control(f"cell-{row}-{column}", letter, letter, "STEP",
                                        center_x - 6 + column * 4, 65,
                                        minimum_z + 22 + row * 6, "CYAN_CONCRETE"))
        target = [("cell-0-0", "M"), ("cell-1-1", "I"),
                  ("cell-2-2", "S"), ("cell-0-3", "T")]
        structure["signs"].append(sign(center_x, 65, minimum_z + 42, "월/화/수/목 행렬"))
        return result(structure, [ordered("weekday-matrix", controls, target, "MIST")],
                      ["요일 행렬에서 기록된 행과 열의 교차 칸을 차례로 밟아 네 글자를 완성하세요."])

    if level == "normal" and sequence == 11:
        items = [("plus-x", "+X", "+X"), ("plus-y", "+Y", "+Y"),
                 ("minus-z", "-Z", "-Z"), ("minus-x", "-X", "-X"),
                 ("minus-y", "-Y", "-Y"), ("plus-z", "+Z", "+Z")]
        controls = button_bank(items, center_x, minimum_z, structure, row_size=6, z_start=36)
        structure["cuboids"].append(cuboid(center_x - 3, 65, minimum_z + 20,
                                            center_x + 3, 70, minimum_z + 24, "AMETHYST_BLOCK"))
        order = [("plus-x", "V"), ("plus-y", "E"), ("minus-z", "C"),
                 ("minus-x", "T"), ("minus-y", "O"), ("plus-z", "R")]
        return result(structure, [ordered("vector-controls", controls, order, "VECTOR")],
                      ["여섯 축 버튼으로 중앙 표식을 이동시키세요. 매 이동에서 드러나는 글자가 액션바에 기록됩니다."])

    if level == "normal" and sequence == 12:
        nodes = {
            "r": (center_x - 12, minimum_z + 16), "o": (center_x - 4, minimum_z + 16),
            "t": (center_x + 4, minimum_z + 16), "a": (center_x + 12, minimum_z + 16),
            "v": (center_x + 12, minimum_z + 27), "end": (center_x + 12, minimum_z + 38),
            "e": (center_x - 12, minimum_z + 27), "k": (center_x - 12, minimum_z + 38),
            "i": (center_x + 4, minimum_z + 27), "n": (center_x + 4, minimum_z + 38),
        }
        controls = [
            control(node, node.upper(), node.upper(), "STEP", x, 65, z,
                    "LIME_CONCRETE" if node in {"r", "o", "t", "a", "v"} else "RED_CONCRETE")
            for node, (x, z) in nodes.items()
        ]
        structure["signs"].append(sign(center_x - 18, 65, minimum_z + 16, "START"))
        for node, (x, z) in nodes.items():
            structure["signs"].append(sign(x, 65, z + 2, node.upper()))
        route = [("r", "R"), ("o", "O"), ("t", "T"), ("a", "A"), ("v", "V"),
                 ("end", ""), ("v", ""), ("a", "A"), ("t", "T"), ("o", "O"), ("r", "R")]
        return result(structure, [ordered("return-route", controls, route, "ROTAVATOR")],
                      ["START에서 길을 따라 END까지 간 뒤 같은 길로 돌아오세요. 귀환 첫 V는 중복 기록하지 않습니다."])

    return None

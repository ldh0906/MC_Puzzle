# 전체 트리거 덤프

280개 `TRIG` 레코드를 실행 순서대로 해석한 결과입니다. 로케이션 이름은 보호 때문에 손상되어 번호로 표기합니다.

## Trigger 1

- Owners: All Players
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Always

Actions:

- Wait 3000 ms
- Display Text Message: 'ㅁ'
- Play WAV 'staredit\\wav\\typing.wav'
- Wait 250 ms
- Display Text Message: '미'
- Play WAV 'staredit\\wav\\typing.wav'
- Wait 250 ms
- Display Text Message: '미ㄱ'
- Play WAV 'staredit\\wav\\typing.wav'
- Wait 250 ms
- Display Text Message: '미구'
- Play WAV 'staredit\\wav\\typing.wav'
- Wait 250 ms
- Display Text Message: '미궁'
- Play WAV 'staredit\\wav\\typing.wav'
- Wait 250 ms
- Display Text Message: '미궁 5'
- Play WAV 'staredit\\wav\\typing.wav'
- Wait 250 ms
- Display Text Message: '미궁 50'
- Play WAV 'staredit\\wav\\typing.wav'
- Wait 250 ms
- Display Text Message: '미궁 50ㄱ'
- Play WAV 'staredit\\wav\\typing.wav'
- Wait 250 ms
- Display Text Message: '미궁 50개'
- Play WAV 'staredit\\wav\\typing.wav'
- Wait 250 ms
- Display Text Message: '미궁 50개ㅇ'
- Play WAV 'staredit\\wav\\typing.wav'
- Wait 250 ms
- Display Text Message: '미궁 50개으'
- Play WAV 'staredit\\wav\\typing.wav'
- Wait 250 ms
- Display Text Message: '미궁 50개의'
- Play WAV 'staredit\\wav\\typing.wav'
- Wait 250 ms
- Display Text Message: '미궁 50개의 ㅂ'
- Play WAV 'staredit\\wav\\typing.wav'
- Wait 250 ms
- Display Text Message: '미궁 50개의 바'
- Play WAV 'staredit\\wav\\typing.wav'
- Wait 250 ms
- Display Text Message: '미궁 50개의 방'
- Play WAV 'staredit\\wav\\typing.wav'
- Wait 10 ms
- Display Text Message: '미궁 50개의 방'
- Wait 10 ms
- Display Text Message: '미궁 50개의 방'
- Wait 10 ms
- Display Text Message: '미궁 50개의 방'
- Wait 10 ms
- Display Text Message: '미궁 50개의 방'
- Wait 10 ms
- Display Text Message: '미궁 50개의 방'
- Play WAV 'staredit\\wav\\typing.wav'
- Wait 5000 ms
- Display Text Message: '미궁 50개의 방 Made by CoOlLuCk-_-'
- Play WAV 'staredit\\wav\\typing.wav'
- Wait 5000 ms
- Display Text Message: '미궁 50개의 방 Made by CoOlLuCk-_-'
- Wait 10 ms
- Display Text Message: ''
- Set Switch 1: Set

## Trigger 2

- Owners: All Players
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Always

Actions:

- Set Switch 1: Clear
- Play WAV 'staredit\\wav\\BGM3.wav'

## Trigger 3

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 1 is Set
- Deaths(Current Player, At most 0, Terran Marine)
- Current Player brings At least 1 × Terran Civilian to Location 18
- Current Player brings At most 0 × Terran Civilian to Location 19

Actions:

- Set Deaths(Current Player, Add 1, Terran Marine)
- Play WAV 'staredit\\wav\\BGM.wav'
- Preserve Trigger

## Trigger 4

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 1 is Set
- Deaths(Current Player, At least 1, Terran Marine)
- Deaths(Current Player, At most 240, Terran Marine)

Actions:

- Set Deaths(Current Player, Add 1, Terran Marine)
- Preserve Trigger

## Trigger 5

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 1 is Set
- Deaths(Current Player, At least 241, Terran Marine)
- Current Player brings At least 1 × Terran Civilian to Location 18
- Current Player brings At most 0 × Terran Civilian to Location 19

Actions:

- Set Deaths(Current Player, Set to 0, Terran Marine)
- Preserve Trigger

## Trigger 6

- Owners: All Players
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 1 is Set

Actions:

- Center View at Location 9
- Give 1 × Zerg Zergling at Location 9: Player 12 → Player 1
- Give 1 × Zerg Zergling at Location 9: Player 12 → Player 2
- Give 1 × Zerg Zergling at Location 9: Player 12 → Player 3
- Give 1 × Zerg Zergling at Location 9: Player 12 → Player 4
- Give 1 × Terran Civilian at Location 30: Player 12 → Player 1
- Give 1 × Terran Civilian at Location 30: Player 12 → Player 2
- Give 1 × Terran Civilian at Location 30: Player 12 → Player 3
- Give 1 × Terran Civilian at Location 30: Player 12 → Player 4
- Wait 1500 ms
- Display Text Message: '힌트를 보고 답을 맞추십시요. 다음 단계로 가기 위해선 현재 스테이지를 클리어하셔야합니다.'

## Trigger 7

- Owners: All Players
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 1 is Cleared

Actions:

- Center View at Location 17
- Preserve Trigger

## Trigger 8

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 1 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 9 with CUWP slot 1

## Trigger 9

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 2 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 20 with CUWP slot 1

## Trigger 10

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 3 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 21 with CUWP slot 1

## Trigger 11

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 4 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 23 with CUWP slot 1

## Trigger 12

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 5 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 25 with CUWP slot 1

## Trigger 13

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 6 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 26 with CUWP slot 1

## Trigger 14

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 7 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 41 with CUWP slot 1

## Trigger 15

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 8 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 88 with CUWP slot 1

## Trigger 16

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 9 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 94 with CUWP slot 1

## Trigger 17

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 10 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 95 with CUWP slot 1

## Trigger 18

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 11 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 98 with CUWP slot 1

## Trigger 19

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 12 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 99 with CUWP slot 1

## Trigger 20

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 13 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 101 with CUWP slot 1

## Trigger 21

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 14 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 102 with CUWP slot 1

## Trigger 22

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 15 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 105 with CUWP slot 1

## Trigger 23

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 16 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 108 with CUWP slot 1

## Trigger 24

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 17 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 117 with CUWP slot 1

## Trigger 25

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 18 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 120 with CUWP slot 1

## Trigger 26

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 19 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 122 with CUWP slot 1

## Trigger 27

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 21 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 135 with CUWP slot 1

## Trigger 28

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 22 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 140 with CUWP slot 1

## Trigger 29

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 23 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 143 with CUWP slot 1

## Trigger 30

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 24 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 146 with CUWP slot 1

## Trigger 31

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 25 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 147 with CUWP slot 1

## Trigger 32

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 26 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 149 with CUWP slot 1

## Trigger 33

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 27 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 151 with CUWP slot 1

## Trigger 34

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 28 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 152 with CUWP slot 1

## Trigger 35

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 29 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 154 with CUWP slot 1

## Trigger 36

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 30 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 156 with CUWP slot 1

## Trigger 37

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 31 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 163 with CUWP slot 1

## Trigger 38

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 32 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 166 with CUWP slot 1

## Trigger 39

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 33 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 169 with CUWP slot 1

## Trigger 40

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 34 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 172 with CUWP slot 1

## Trigger 41

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 35 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 175 with CUWP slot 1

## Trigger 42

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 36 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 178 with CUWP slot 1

## Trigger 43

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 37 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 181 with CUWP slot 1

## Trigger 44

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 38 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 186 with CUWP slot 1

## Trigger 45

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 40 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 196 with CUWP slot 1

## Trigger 46

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 41 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 197 with CUWP slot 1

## Trigger 47

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 42 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 202 with CUWP slot 1

## Trigger 48

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 43 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 207 with CUWP slot 1

## Trigger 49

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 44 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 212 with CUWP slot 1

## Trigger 50

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 45 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 214 with CUWP slot 1

## Trigger 51

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 46 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 219 with CUWP slot 1

## Trigger 52

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 47 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 220 with CUWP slot 1

## Trigger 53

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 48 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 223 with CUWP slot 1

## Trigger 54

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 49 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 226 with CUWP slot 1

## Trigger 55

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 50 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 227 with CUWP slot 1

## Trigger 56

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 51 is Set
- Deaths(Current Player, At most 84, Zerg Sunken Colony)

Actions:

- Set Deaths(Current Player, Add 1, Zerg Sunken Colony)
- Preserve Trigger

## Trigger 57

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 51 is Set
- Deaths(Player 7, At least 85, Zerg Sunken Colony)

Actions:

- Display Text Message: '모든 스테이지를 클리어 하셨습니다. 당신은 정말 대단한 두뇌를 지니셨군요! 못난 제작자, 이만 물러가겠습니다. Made by CoOlLuCk-_-'
- Play WAV 'staredit\\wav\\Victory1.wav'
- Victory

## Trigger 58

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 40 is Set

Actions:

- Create 1 × Map Revealer for Current Player at Location 196 with CUWP slot 1
- Give 1 × Flag at Location 196: Player 12 → Player 1
- Give 1 × Flag at Location 196: Player 12 → Player 3
- Give 1 × Flag at Location 196: Player 12 → Player 2
- Give 1 × Flag at Location 196: Player 12 → Player 4

## Trigger 59

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Force 1 brings At least 1 × Zerg Zergling to Location 88
- Deaths(Player 7, At most 119, Terran Missile Turret)

Actions:

- Set Deaths(Current Player, Add 1, Terran Missile Turret)
- Preserve Trigger

## Trigger 60

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 29 is Set

Actions:

- Move All × Zerg Egg owned by Player 12: Anywhere (L64) → Location 155

## Trigger 61

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Always

Actions:

- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Preserve Trigger

## Trigger 62

- Owners: Player 7
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Always

Actions:

- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Wait 0 ms
- Preserve Trigger

## Trigger 63

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Deaths(Player 7, At most 4, Terran Medic)

Actions:

- Set Alliance: Player 1 treats Player 2 as Enemy
- Set Alliance: Player 2 treats Player 2 as Enemy
- Set Alliance: Player 3 treats Player 2 as Enemy
- Set Alliance: Player 4 treats Player 2 as Enemy
- Set Alliance: Player 5 treats Player 2 as Enemy
- Set Alliance: Player 6 treats Player 2 as Enemy
- Set Alliance: Force 1 treats Player 3 as Enemy
- Run AI script '+Vi0'
- Run AI script '+Vi1'
- Run AI script '+Vi2'
- Run AI script '+Vi3'
- Run AI script '+Vi6'
- Set Deaths(Player 7, Add 1, Terran Medic)
- Preserve Trigger

## Trigger 64

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Deaths(Player 7, At least 1, Terran Medic)
- Deaths(Player 7, At most 60, Terran Medic)

Actions:

- Set Deaths(Player 7, Add 1, Terran Medic)
- Preserve Trigger

## Trigger 65

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Deaths(Player 7, At least 37, Terran Medic)

Actions:

- Set Deaths(Player 7, Set to 0, Terran Medic)
- Preserve Trigger

## Trigger 66

- Owners: Player 5, Player 6
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Always

Actions:

- Modify Unit Hit Points: All × Devouring One owned by Current Player at Location 26 → 100%
- Modify Unit Hit Points: All × Jim Raynor (Marine) owned by Current Player at Location 108 → 100%
- Modify Unit Hit Points: All × Terran Marine owned by Current Player at Location 108 → 100%
- Modify Unit Hit Points: All × Zerg Ultralisk owned by Player 6 at Location 122 → 100%
- Modify Unit Energy: All × Sarah Kerrigan owned by Player 5 at Location 41 → 100%
- Modify Unit Energy: All × Tom Kazansky owned by Player 5 at Anywhere (L64) → 100%
- Order Tom Kazansky owned by Player 5 at Anywhere (L64): Move to Location 148
- Move Anywhere (L64) to Bengalaas owned by All Players at Location 148
- Modify Unit Shield Points: All × Buildings owned by Player 12 at Anywhere (L64) → 0%
- Preserve Trigger

## Trigger 67

- Owners: Player 5
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Always

Actions:

- Set Alliance: Player 6 treats Player 1 as Enemy
- Move Location 25 to Protoss Observer owned by Player 5 at Location 31
- Preserve Trigger

## Trigger 68

- Owners: Player 6
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Always

Actions:

- Set Alliance: Player 5 treats Player 1 as Enemy
- Preserve Trigger

## Trigger 69

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 99 is Cleared
- Force 1 brings At least 1 × Buildings to Location 90

Actions:

- Give All × Buildings at Location 90: Force 1 → Player 12
- Preserve Trigger

## Trigger 70

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Switch 99 is Set
- Player 12 brings At least 1 × Buildings to Location 90

Actions:

- Give 1 × Protoss Pylon at Location 90: Player 12 → Player 1
- Give 1 × Protoss Pylon at Location 90: Player 12 → Player 2
- Give 1 × Protoss Pylon at Location 90: Player 12 → Player 3
- Give 1 × Protoss Pylon at Location 90: Player 12 → Player 4
- Give 1 × Protoss Fleet Beacon at Location 90: Player 12 → Player 1
- Give 1 × Protoss Fleet Beacon at Location 90: Player 12 → Player 2
- Give 1 × Protoss Fleet Beacon at Location 90: Player 12 → Player 3
- Give 1 × Protoss Fleet Beacon at Location 90: Player 12 → Player 4
- Give 1 × Protoss Stargate at Location 90: Player 12 → Player 1
- Give 1 × Protoss Stargate at Location 90: Player 12 → Player 2
- Give 1 × Protoss Stargate at Location 90: Player 12 → Player 3
- Give 1 × Protoss Stargate at Location 90: Player 12 → Player 4
- Give 1 × Protoss Arbiter Tribunal at Location 90: Player 12 → Player 1
- Give 1 × Protoss Arbiter Tribunal at Location 90: Player 12 → Player 2
- Give 1 × Protoss Arbiter Tribunal at Location 90: Player 12 → Player 3
- Give 1 × Protoss Arbiter Tribunal at Location 90: Player 12 → Player 4
- Preserve Trigger

## Trigger 71

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 10
- Force 1 brings At least 1 × Zerg Zergling to Location 10

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 10 → Location 9
- Preserve Trigger

## Trigger 72

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 11
- Force 1 brings At least 1 × Zerg Zergling to Location 11

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 11 → Location 20
- Preserve Trigger

## Trigger 73

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 16
- Force 1 brings At least 1 × Zerg Zergling to Location 16

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 16 → Location 21
- Preserve Trigger

## Trigger 74

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 24
- Force 1 brings At least 1 × Zerg Zergling to Location 24

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 24 → Location 23
- Preserve Trigger

## Trigger 75

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 27
- Force 1 brings At least 1 × Zerg Zergling to Location 27

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 27 → Location 25
- Preserve Trigger

## Trigger 76

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 28
- Force 1 brings At least 1 × Zerg Zergling to Location 28

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 28 → Location 26
- Preserve Trigger

## Trigger 77

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 42
- Force 1 brings At least 1 × Zerg Zergling to Location 42

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 42 → Location 41
- Preserve Trigger

## Trigger 78

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 43
- Force 1 brings At least 1 × Zerg Zergling to Location 43

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 43 → Location 88
- Preserve Trigger

## Trigger 79

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 44
- Force 1 brings At least 1 × Zerg Zergling to Location 44

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 44 → Location 94
- Preserve Trigger

## Trigger 80

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 45
- Force 1 brings At least 1 × Zerg Zergling to Location 45

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 45 → Location 95
- Preserve Trigger

## Trigger 81

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 46
- Force 1 brings At least 1 × Zerg Zergling to Location 46

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 46 → Location 98
- Preserve Trigger

## Trigger 82

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 47
- Force 1 brings At least 1 × Zerg Zergling to Location 47

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 47 → Location 99
- Preserve Trigger

## Trigger 83

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 48
- Force 1 brings At least 1 × Zerg Zergling to Location 48

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 48 → Location 101
- Preserve Trigger

## Trigger 84

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 50
- Force 1 brings At least 1 × Zerg Zergling to Location 50

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 50 → Location 105
- Preserve Trigger

## Trigger 85

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 51
- Force 1 brings At least 1 × Zerg Zergling to Location 51

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 51 → Location 108
- Preserve Trigger

## Trigger 86

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 52
- Force 1 brings At least 1 × Zerg Zergling to Location 52

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 52 → Location 117
- Preserve Trigger

## Trigger 87

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 53
- Force 1 brings At least 1 × Zerg Zergling to Location 53

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 53 → Location 120
- Preserve Trigger

## Trigger 88

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 54
- Force 1 brings At least 1 × Zerg Zergling to Location 54

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 54 → Location 122
- Preserve Trigger

## Trigger 89

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 55
- Force 1 brings At least 1 × Zerg Zergling to Location 55

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 55 → Location 130
- Preserve Trigger

## Trigger 90

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 56
- Force 1 brings At least 1 × Zerg Zergling to Location 56

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 56 → Location 135
- Preserve Trigger

## Trigger 91

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 57
- Force 1 brings At least 1 × Zerg Zergling to Location 57

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 57 → Location 140
- Preserve Trigger

## Trigger 92

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 58
- Force 1 brings At least 1 × Zerg Zergling to Location 58

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 58 → Location 143
- Preserve Trigger

## Trigger 93

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 59
- Force 1 brings At least 1 × Zerg Zergling to Location 59

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 59 → Location 146
- Preserve Trigger

## Trigger 94

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 60
- Force 1 brings At least 1 × Zerg Zergling to Location 60

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 60 → Location 147
- Preserve Trigger

## Trigger 95

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 61
- Force 1 brings At least 1 × Zerg Zergling to Location 61

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 61 → Location 149
- Preserve Trigger

## Trigger 96

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 62
- Force 1 brings At least 1 × Zerg Zergling to Location 62

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 62 → Location 151
- Preserve Trigger

## Trigger 97

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 63
- Force 1 brings At least 1 × Zerg Zergling to Location 63

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 63 → Location 152
- Preserve Trigger

## Trigger 98

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 65
- Force 1 brings At least 1 × Zerg Zergling to Location 65

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 65 → Location 154
- Preserve Trigger

## Trigger 99

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 66
- Force 1 brings At least 1 × Zerg Zergling to Location 66

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 66 → Location 156
- Preserve Trigger

## Trigger 100

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 67
- Force 1 brings At least 1 × Zerg Zergling to Location 67

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 67 → Location 163
- Preserve Trigger

## Trigger 101

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 68
- Force 1 brings At least 1 × Zerg Zergling to Location 68

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 68 → Location 166
- Preserve Trigger

## Trigger 102

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 69
- Force 1 brings At least 1 × Zerg Zergling to Location 69

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 69 → Location 169
- Preserve Trigger

## Trigger 103

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 70
- Force 1 brings At least 1 × Zerg Zergling to Location 70

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 70 → Location 172
- Preserve Trigger

## Trigger 104

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 71
- Force 1 brings At least 1 × Zerg Zergling to Location 71

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 71 → Location 175
- Preserve Trigger

## Trigger 105

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 72
- Force 1 brings At least 1 × Zerg Zergling to Location 72

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 72 → Location 178
- Preserve Trigger

## Trigger 106

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 73
- Force 1 brings At least 1 × Zerg Zergling to Location 73

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 73 → Location 181
- Preserve Trigger

## Trigger 107

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 74
- Force 1 brings At least 1 × Zerg Zergling to Location 74

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 74 → Location 186
- Preserve Trigger

## Trigger 108

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 75
- Force 1 brings At least 1 × Zerg Zergling to Location 75

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 75 → Location 188
- Preserve Trigger

## Trigger 109

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 76
- Force 1 brings At least 1 × Zerg Zergling to Location 76

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 76 → Location 196
- Preserve Trigger

## Trigger 110

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 77
- Force 1 brings At least 1 × Zerg Zergling to Location 77

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 77 → Location 197
- Preserve Trigger

## Trigger 111

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 78
- Force 1 brings At least 1 × Zerg Zergling to Location 78

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 78 → Location 202
- Preserve Trigger

## Trigger 112

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 79
- Force 1 brings At least 1 × Zerg Zergling to Location 79

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 79 → Location 207
- Preserve Trigger

## Trigger 113

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 80
- Force 1 brings At least 1 × Zerg Zergling to Location 80

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 80 → Location 212
- Preserve Trigger

## Trigger 114

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 81
- Force 1 brings At least 1 × Zerg Zergling to Location 81

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 81 → Location 214
- Preserve Trigger

## Trigger 115

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 82
- Force 1 brings At least 1 × Zerg Zergling to Location 82

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 82 → Location 219
- Preserve Trigger

## Trigger 116

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 83
- Force 1 brings At least 1 × Zerg Zergling to Location 83

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 83 → Location 220
- Preserve Trigger

## Trigger 117

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 84
- Force 1 brings At least 1 × Zerg Zergling to Location 84

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 84 → Location 223
- Preserve Trigger

## Trigger 118

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 1 × Terran Supply Depot to Location 85
- Force 1 brings At least 1 × Zerg Zergling to Location 85

Actions:

- Display Text Message: '넘어가실 수 없습니다.'
- Move All × Zerg Zergling owned by Force 1: Location 85 → Location 226
- Preserve Trigger

## Trigger 119

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Force 1 brings At least 1 × Zerg Zergling to Location 1
- Force 1 brings At least 1 × Zerg Zergling to Location 4
- Force 1 brings At least 1 × Zerg Zergling to Location 2
- Force 1 brings At least 1 × Zerg Zergling to Location 3
- Player 12 brings At least 1 × Terran Armory to Location 9

Actions:

- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 10
- Kill All × Terran Armory owned by Player 12 at Location 9
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 1 클리어! 지형이 약간 다른 것을 잘 찾아내셨군요!'
- Set Switch 2: Set
- Comment: ''

## Trigger 120

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 1 brings At least 1 × Zerg Zergling to Location 7
- Player 2 brings At least 1 × Zerg Zergling to Location 8
- Player 3 brings At least 1 × Zerg Zergling to Location 6
- Player 4 brings At least 1 × Zerg Zergling to Location 5
- Player 12 brings At least 1 × Terran Engineering Bay to Location 20

Actions:

- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 11
- Kill All × Terran Engineering Bay owned by Player 12 at Location 20
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 2 클리어! 엔지니어링 베이에서는 바이오닉 업그레이드가 일어나죠!'
- Set Switch 3: Set
- Comment: ''

## Trigger 121

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Force 1 brings At least 1 × Zerg Zergling to Location 12
- Force 1 brings At least 1 × Zerg Zergling to Location 13
- Force 1 brings At least 1 × Zerg Zergling to Location 14
- Force 1 brings At least 1 × Zerg Zergling to Location 15
- Player 12 brings At most 0 × Terran Academy to Location 21
- Player 12 brings At least 1 × Terran Supply Depot to Location 16

Actions:

- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 16
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 3 클리어! 막혔다면 부수면 되겠지요.'
- Set Switch 4: Set
- Comment: ''

## Trigger 122

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Force 1 brings At least 4 × Zerg Zergling to Location 22
- Player 12 brings At least 1 × Zerg Ultralisk Cavern to Location 23

Actions:

- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 24
- Kill All × Zerg Ultralisk Cavern owned by Player 12 at Location 23
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 4 클리어! 버로우를 이용하면 좁은 곳에도 모일 수 있죠.'
- Set Switch 5: Set
- Comment: ''

## Trigger 123

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings Exactly 8 × Protoss Dark Templar to Location 25
- Player 12 brings At least 1 × Protoss Photon Cannon to Location 25

Actions:

- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 27
- Kill All × Protoss Photon Cannon owned by Player 12 at Location 25
- Kill All × Protoss Observer owned by Force 1 at Anywhere (L64)
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 5 클리어! 옵저버를 찾아내셨군요!'
- Set Switch 6: Set
- Comment: ''

## Trigger 124

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 5 brings Exactly 0 × Devouring One to Location 26
- Player 6 brings Exactly 0 × Devouring One to Location 26
- Player 12 brings At least 1 × Protoss Arbiter Tribunal to Location 26

Actions:

- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 28
- Kill All × Protoss Arbiter Tribunal owned by Player 12 at Location 26
- Kill All × Protoss Dark Templar owned by Force 1 at Anywhere (L64)
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 6 클리어! 개를 잡으려면 개 사냥꾼을 불러야죠!'
- Set Switch 7: Set
- Comment: ''

## Trigger 125

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Force 1 brings At least 1 × Zerg Zergling to Location 40
- Force 1 brings At least 1 × Zerg Zergling to Location 39
- Force 1 brings At least 1 × Zerg Zergling to Location 37
- Force 1 brings At least 1 × Zerg Zergling to Location 38
- Player 12 brings At least 1 × Protoss Observatory to Location 41

Actions:

- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 42
- Kill All × Protoss Observatory owned by Player 12 at Location 41
- Kill All × Zeratul owned by Player 12 at Location 41
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 7 클리어! 무언가가 걸리적 거리네요.'
- Set Switch 8: Set
- Comment: ''

## Trigger 126

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Force 1 brings At least 1 × Zerg Zergling to Location 88
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 88
- Deaths(Player 7, At least 120, Terran Missile Turret)

Actions:

- Move All × Zerg Zergling owned by Force 1: Anywhere (L64) → Location 88
- Center View at Location 88
- Minimap Ping at Location 90
- Play WAV 'staredit\\wav\\typing.wav'
- Display Text Message: '이번 스테이지는 답이 되는 수를 산출해내셔야 합니다. 수의 산출을 위해 6시 방향에 산출기가 지급됩니다.'
- Set Switch 99: Set
- Comment: ''

## Trigger 127

- Owners: Player 1, Player 2, Player 3, Player 4, Player 5
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Protoss Observer to Location 32

Actions:

- Move All × Protoss Observer owned by Current Player: Location 32 → Location 36
- Preserve Trigger

## Trigger 128

- Owners: Player 1, Player 2, Player 3, Player 4, Player 5
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Protoss Observer to Location 35

Actions:

- Move All × Protoss Observer owned by Current Player: Location 35 → Location 36
- Preserve Trigger

## Trigger 129

- Owners: Player 1, Player 2, Player 3, Player 4, Player 5
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Protoss Observer to Location 34

Actions:

- Move All × Protoss Observer owned by Current Player: Location 34 → Location 36
- Preserve Trigger

## Trigger 130

- Owners: Player 1, Player 2, Player 3, Player 4, Player 5
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Protoss Observer to Location 33

Actions:

- Move All × Protoss Observer owned by Current Player: Location 33 → Location 36
- Preserve Trigger

## Trigger 131

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 31

Actions:

- Give All × Protoss Observer at Location 25: Player 5 → Current Player

## Trigger 132

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 29
- Player 12 brings At least 1 × Protoss Arbiter Tribunal to Location 26
- Player 12 brings At most 0 × Protoss Photon Cannon to Location 25

Actions:

- Give All × Protoss Dark Templar at Location 25: Player 12 → Current Player
- Display Text Message: '개 사냥꾼을 고용하셨습니다.'
- Play WAV 'staredit\\wav\\typing.wav'
- Center View at Location 25

## Trigger 133

- Owners: Player 5
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Deaths(Player 5, Exactly 0, Terran Marine)

Actions:

- Run AI script 'JYDg' at Location 25
- Set Deaths(Player 5, Add 1, Terran Marine)
- Preserve Trigger

## Trigger 134

- Owners: Player 5
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Deaths(Player 5, At least 1, Terran Marine)
- Deaths(Player 5, At most 60, Terran Marine)

Actions:

- Set Deaths(Player 5, Add 1, Terran Marine)
- Preserve Trigger

## Trigger 135

- Owners: Player 5
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Deaths(Player 5, At least 61, Terran Marine)

Actions:

- Set Deaths(Player 5, Set to 0, Terran Marine)
- Preserve Trigger

## Trigger 136

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Deaths(Player 7, At least 1, Terran Nuclear Silo)

Actions:

- Set Deaths(Player 7, Set to 0, Terran Nuclear Silo)
- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 43
- Kill All × Zerg Nydus Canal owned by Player 12 at Location 88
- Kill All × Zerg Greater Spire owned by Player 12 at Location 88
- Set Resources(Force 1, Set to 0 Minerals)
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 8 클리어! 키패드에 쳐봤다면 바로 알 수 있었죠.'
- Set Switch 99: Clear
- Set Switch 9: Set
- Comment: ''

## Trigger 137

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Force 1 brings At least 1 × Zerg Zergling to Location 93
- Force 1 brings At least 1 × Zerg Zergling to Location 89
- Force 1 brings At least 1 × Zerg Zergling to Location 91
- Force 1 brings At least 1 × Zerg Zergling to Location 92
- Player 12 brings At least 1 × Protoss Citadel of Adun to Location 94

Actions:

- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 44
- Kill All × Protoss Citadel of Adun owned by Player 12 at Location 94
- Kill All × Men owned by Player 12 at Location 94
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 9 클리어! 공격, 방어도 아닌 계급이 다르네요!'
- Set Switch 10: Set
- Comment: ''

## Trigger 138

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Force 1 brings At least 1 × Zerg Zergling to Location 9
- Player 12 brings At least 1 × Zerg Queen's Nest to Location 95
- Switch 10 is Set

Actions:

- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 45
- Kill All × Zerg Queen's Nest owned by Player 12 at Location 95
- Kill All × Zerg Defiler owned by Player 12 at Location 95
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 10 클리어! 연어는 거슬러 올라가는 특성이 있습니다.'
- Set Switch 11: Set
- Comment: ''

## Trigger 139

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At most 0 × Terran Bunker to Location 98

Actions:

- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 46
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 11 클리어! 4번째 영역에만 힌트가 있어 약간 다르네요!'
- Set Switch 12: Set
- Comment: ''

## Trigger 140

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Force 1 brings At least 4 × Zerg Zergling to Location 96
- Player 12 brings At least 1 × Protoss Citadel of Adun to Location 99

Actions:

- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 47
- Kill All × Protoss Citadel of Adun owned by Player 12 at Location 99
- Kill All × Men owned by Player 12 at Location 99
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 12 클리어! 다른 유닛은 모두 영웅인데, 고스트만 일반 유닛이군요'
- Set Switch 13: Set
- Set Switch 99: Set
- Comment: ''

## Trigger 141

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Deaths(Player 7, At least 1, Terran Physics Lab)

Actions:

- Set Deaths(Player 7, Set to 0, Terran Physics Lab)
- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 48
- Kill All × Zerg Nydus Canal owned by Player 12 at Location 101
- Kill All × Zerg Greater Spire owned by Player 12 at Location 101
- Set Resources(Force 1, Set to 0 Minerals)
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 13 클리어! 각 자릿수 별로 다른 특징이 있네요'
- Set Switch 99: Clear
- Set Switch 14: Set
- Comment: ''

## Trigger 142

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Force 1 brings At least 1 × Zerg Zergling to Location 49
- Player 12 brings At least 1 × Protoss Cybernetics Core to Location 102

Actions:

- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 49
- Kill All × Protoss Cybernetics Core owned by Player 12 at Location 102
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 14 클리어! 말 그대로 넘어가면 되겠군요'
- Set Switch 15: Set
- Comment: ''

## Trigger 143

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Force 1 brings Exactly 2 × Zerg Zergling to Location 106
- Force 1 brings Exactly 2 × Zerg Zergling to Location 107
- Player 12 brings At least 1 × Terran Supply Depot to Location 50

Actions:

- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 50
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 15 클리어! 공정하게 2:2로 싸우면 되겠군요'
- Set Switch 16: Set
- Comment: ''

## Trigger 144

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 6 brings Exactly 2 × Terran Marine to Location 108
- Player 12 brings At least 1 × Zerg Evolution Chamber to Location 108

Actions:

- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 51
- Kill All × Zerg Evolution Chamber owned by Player 12 at Location 108
- Kill All × Jim Raynor (Marine) owned by Player 5 at Location 108
- Kill All × Terran Marine owned by Player 6 at Location 108
- Kill All × Protoss Archon owned by Force 1 at Anywhere (L64)
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 16 클리어! 공정 사회를 지향합니다'
- Set Switch 17: Set
- Comment: ''

## Trigger 145

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Force 1 brings At least 1 × Zerg Zergling to Location 113
- Force 1 brings At least 1 × Zerg Zergling to Location 114
- Force 1 brings At least 1 × Zerg Zergling to Location 115
- Force 1 brings At least 1 × Zerg Zergling to Location 116
- Player 12 brings At least 1 × Zerg Spawning Pool to Location 117

Actions:

- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 52
- Kill All × Zerg Spawning Pool owned by Player 12 at Location 117
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 17 클리어! 저는 저그 소속입니다.'
- Set Switch 18: Set
- Comment: ''

## Trigger 146

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At most 1 × Terran Supply Depot to Location 53
- Player 12 brings At least 1 × Protoss Forge to Location 120

Actions:

- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 53
- Kill All × Protoss Forge owned by Player 12 at Location 120
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 18 클리어! 네... 여러분을 낚시하는 중이였습니다.'
- Set Switch 19: Set
- Comment: ''

## Trigger 147

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 5 brings At least 1 × Zerg Scourge to Location 54
- Player 12 brings At least 1 × Terran Supply Depot to Location 54

Actions:

- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 54
- Kill All × Zerg Scourge owned by Player 5 at Anywhere (L64)
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 19 클리어! 스컬지를 잘 운반하셨군요!'
- Set Switch 20: Set
- Comment: ''

## Trigger 148

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Force 1 brings At least 1 × Zerg Zergling to Location 112
- Force 1 brings At least 1 × Zerg Zergling to Location 111
- Force 1 brings At least 1 × Zerg Zergling to Location 110
- Force 1 brings At least 1 × Zerg Zergling to Location 109
- Player 12 brings At least 1 × Terran Supply Depot to Location 55

Actions:

- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 55
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 20 클리어! 유일하게 패턴이 4번 나타나며 시야가 좁아지네요'
- Set Switch 21: Set
- Comment: ''

## Trigger 149

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Force 1 brings At least 1 × Zerg Zergling to Location 131
- Force 1 brings At least 1 × Zerg Zergling to Location 132
- Force 1 brings At least 1 × Zerg Zergling to Location 133
- Force 1 brings At least 1 × Zerg Zergling to Location 134
- Player 12 brings At least 1 × Protoss Shield Battery to Location 135

Actions:

- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 56
- Kill All × Men owned by Player 12 at Location 135
- Kill All × Protoss Shield Battery owned by Player 12 at Location 135
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 21 클리어! 모두 수송선에 8기가 탈 수 있는 유닛들입니다.'
- Set Switch 22: Set
- Comment: ''

## Trigger 150

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Force 1 brings At least 1 × Zerg Zergling to Location 136
- Force 1 brings At least 1 × Zerg Zergling to Location 137
- Force 1 brings At least 1 × Zerg Zergling to Location 138
- Force 1 brings At least 1 × Zerg Zergling to Location 139
- Player 12 brings At least 1 × Protoss Citadel of Adun to Location 140

Actions:

- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 57
- Kill All × Men owned by Player 12 at Location 140
- Kill All × Men owned by Player 8 at Location 140
- Kill All × Protoss Citadel of Adun owned by Player 12 at Location 140
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 22 클리어! 적으로 나타는 유닛들이 있군요!'
- Set Switch 23: Set
- Set Switch 99: Set
- Comment: ''

## Trigger 151

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Deaths(Player 7, At least 1, Terran Refinery)

Actions:

- Set Deaths(Player 7, Set to 0, Terran Refinery)
- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 58
- Kill All × Zerg Nydus Canal owned by Player 12 at Location 143
- Kill All × Zerg Greater Spire owned by Player 12 at Location 143
- Set Resources(Force 1, Set to 0 Minerals)
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 23 클리어! 거울에 비춰보면 392라는 숫자가 나오는군요'
- Set Switch 24: Set
- Comment: ''

## Trigger 152

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Deaths(Player 7, At least 1, Terran Science Facility)

Actions:

- Set Deaths(Player 7, Set to 0, Terran Science Facility)
- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 59
- Kill All × Zerg Nydus Canal owned by Player 12 at Location 146
- Kill All × Zerg Greater Spire owned by Player 12 at Location 146
- Set Resources(Force 1, Set to 0 Minerals)
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 24 클리어! 약간은 다른 사칙연산입니다'
- Set Switch 25: Set
- Set Switch 99: Clear
- Comment: ''

## Trigger 153

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- All Players brings At most 0 × Bengalaas to Location 148
- Player 12 brings At least 1 × Terran Supply Depot to Location 60

Actions:

- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 60
- Kill All × Tom Kazansky owned by Player 5 at Anywhere (L64)
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 25 클리어! 마스터 키를 잘 사용하셨는지요.'
- Set Switch 26: Set
- Comment: ''

## Trigger 154

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Force 1 brings At least 4 × Zerg Zergling to Location 150
- Player 12 brings At least 1 × Terran Supply Depot to Location 61

Actions:

- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 61
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 26 클리어! 어이없을지 모르지만 타일이 왼쪽 위를 가르키고있군요'
- Set Switch 27: Set
- Comment: ''

## Trigger 155

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At most 0 × Protoss Robotics Facility to Location 151
- Player 12 brings At least 1 × Terran Supply Depot to Location 62

Actions:

- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 62
- Kill All × Samir Duran owned by Player 12 at Location 120
- Kill All × Zerg Devourer owned by Player 5 at Location 120
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 27 클리어! 인생의 진리가 無 라면 無 의 상태를 만들어야죠'
- Set Switch 28: Set
- Comment: ''

## Trigger 156

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Force 1 brings At least 1 × Zerg Zergling to Location 153
- Player 12 brings At least 1 × Terran Supply Depot to Location 63

Actions:

- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 63
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 28 클리어! 벽 뒤에 비밀 공간이 숨어있군요'
- Set Switch 29: Set
- Comment: ''

## Trigger 157

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At most 0 × Zerg Egg to Location 155
- Player 12 brings At least 1 × Terran Supply Depot to Location 65
- Force 1 has At least 1 kills of Zerg Egg

Actions:

- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 65
- Kill All × Norad II (Crashed) owned by Player 12 at Location 154
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 29 클리어! 제가 어이없게도라는 말을 했었던 26탄에 답이 있네요'
- Set Switch 30: Set
- Comment: ''

## Trigger 158

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Force 1 brings At least 1 × Zerg Zergling to Location 157
- Force 1 brings At least 1 × Zerg Zergling to Location 158
- Force 1 brings At least 1 × Zerg Zergling to Location 159
- Force 1 brings At least 1 × Zerg Zergling to Location 160
- Player 12 brings At least 1 × Ion Cannon to Location 156

Actions:

- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 66
- Kill All × Ion Cannon owned by Player 12 at Location 156
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 30 클리어! 뻐꾸기가 3번 울었으니 3시를 나타내야죠!'
- Set Switch 31: Set
- Set Switch 99: Set
- Comment: ''

## Trigger 159

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Deaths(Player 7, At least 1, Terran Science Vessel)

Actions:

- Set Deaths(Player 7, Set to 0, Terran Science Vessel)
- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 67
- Kill All × Zerg Nydus Canal owned by Player 12 at Location 163
- Kill All × Zerg Greater Spire owned by Player 12 at Location 163
- Set Resources(Force 1, Set to 0 Minerals)
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 31 클리어! 당신의 파일런을 보셨다면 아셨을 것입니다'
- Set Switch 32: Set
- Comment: ''

## Trigger 160

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Deaths(Player 7, At least 1, Terran Siege Tank (Siege Mode))

Actions:

- Set Deaths(Player 7, Set to 0, Terran Siege Tank (Siege Mode))
- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 68
- Kill All × Zerg Nydus Canal owned by Player 12 at Location 166
- Kill All × Zerg Greater Spire owned by Player 12 at Location 166
- Set Resources(Force 1, Set to 0 Minerals)
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 32 클리어! 1부터 했다면 답을 몰랐겠지요'
- Set Switch 33: Set
- Comment: ''

## Trigger 161

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Deaths(Player 7, At least 1, Terran Starport)

Actions:

- Set Deaths(Player 7, Set to 0, Terran Starport)
- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 69
- Kill All × Zerg Nydus Canal owned by Player 12 at Location 169
- Kill All × Zerg Greater Spire owned by Player 12 at Location 169
- Set Resources(Force 1, Set to 0 Minerals)
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 33 클리어! 모든 영어가 키보드의 윗 부분이네요!'
- Set Switch 34: Set
- Comment: ''

## Trigger 162

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Deaths(Player 7, At least 1, Terran Valkyrie)

Actions:

- Set Deaths(Player 7, Set to 0, Terran Valkyrie)
- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 70
- Kill All × Zerg Nydus Canal owned by Player 12 at Location 172
- Kill All × Zerg Greater Spire owned by Player 12 at Location 172
- Set Resources(Force 1, Set to 0 Minerals)
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 34 클리어! 스타게이트에서 그대로 sanctuary를 치시면 됩니다'
- Set Switch 35: Set
- Comment: ''

## Trigger 163

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Deaths(Player 7, At least 1, Tom Kazansky)

Actions:

- Set Deaths(Player 7, Set to 0, Tom Kazansky)
- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 71
- Kill All × Zerg Nydus Canal owned by Player 12 at Location 175
- Kill All × Zerg Greater Spire owned by Player 12 at Location 175
- Set Resources(Force 1, Set to 0 Minerals)
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 35 클리어! 철수의 생년월일을 보니 빠른 62년생입니다'
- Set Switch 36: Set
- Comment: ''

## Trigger 164

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Deaths(Player 7, At least 1, Torrasque)

Actions:

- Set Deaths(Player 7, Set to 0, Torrasque)
- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 72
- Kill All × Zerg Nydus Canal owned by Player 12 at Location 178
- Kill All × Zerg Greater Spire owned by Player 12 at Location 178
- Set Resources(Force 1, Set to 0 Minerals)
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 36 클리어! 2011년 11월 10일, 70만 수험생의 모든 것이 끝나는 수능 날입니다'
- Set Switch 37: Set
- Comment: ''

## Trigger 165

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Deaths(Player 7, At least 1, Zerg Ultralisk)

Actions:

- Set Deaths(Player 7, Set to 0, Zerg Ultralisk)
- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 73
- Kill All × Zerg Nydus Canal owned by Player 12 at Location 181
- Kill All × Zerg Greater Spire owned by Player 12 at Location 181
- Set Resources(Force 1, Set to 0 Minerals)
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 37 클리어! 현재 4행 4열의 위치에 있습니다(row는 행, column은 렬입니다)'
- Set Switch 38: Set
- Set Switch 99: Clear
- Comment: ''

## Trigger 166

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Force 1 brings At least 1 × Zerg Zergling to Location 183
- Force 1 brings At least 1 × Zerg Zergling to Location 185
- Force 1 brings At least 1 × Zerg Zergling to Location 182
- Force 1 brings At least 1 × Zerg Zergling to Location 184
- Player 12 brings At least 1 × Protoss Robotics Support Bay to Location 186

Actions:

- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 74
- Kill All × Protoss Robotics Support Bay owned by Player 12 at Location 186
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 38 클리어! 쓰여진 한자는 어긋날 간입니다.'
- Set Switch 39: Set
- Comment: ''

## Trigger 167

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Force 1 brings At least 1 × Zerg Zergling to Location 187
- Force 1 brings At least 1 × Zerg Zergling to Location 189
- Force 1 brings At least 1 × Zerg Zergling to Location 191
- Force 1 brings At least 1 × Zerg Zergling to Location 190
- Player 12 brings At least 1 × Terran Supply Depot to Location 75

Actions:

- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 75
- Kill All × Lurker Egg owned by Player 12 at Location 188
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 39 클리어! 답은 은근히 쉬운 곳에 있습니다'
- Set Switch 40: Set
- Comment: ''

## Trigger 168

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Force 1 brings At least 1 × Zerg Zergling to Location 196

Actions:

- Remove All × Spider Mine owned by Player 12 at Location 196

## Trigger 169

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 12 brings At least 4 × Flag to Location 196
- Player 12 brings At least 1 × Terran Supply Depot to Location 76
- Player 12 brings At most 0 × Spider Mine to Location 196

Actions:

- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 76
- Kill All × Flag owned by Player 12 at Location 196
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 40 클리어! 이제 40탄을 넘었군요'
- Set Switch 41: Set
- Comment: ''

## Trigger 170

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Force 1 brings At most 0 × Zerg Zergling to Location 197
- Player 12 brings At most 0 × Zerg Hydralisk Den to Location 197
- Player 12 brings At least 1 × Terran Supply Depot to Location 77

Actions:

- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 77
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 41 클리어! 정말 아무것도 없어야합니다'
- Set Switch 42: Set
- Comment: ''

## Trigger 171

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 1 brings At least 1 × Zerg Zergling to Location 198
- Player 2 brings At least 1 × Zerg Zergling to Location 199
- Player 3 brings At least 1 × Zerg Zergling to Location 201
- Player 4 brings At least 1 × Zerg Zergling to Location 200
- Player 12 brings At least 1 × Zerg Cerebrate to Location 202

Actions:

- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 78
- Kill All × Zerg Cerebrate owned by Player 12 at Location 202
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 42 클리어! 심오하게 가스가 바뀌는군요'
- Set Resources(Force 1, Set to 0 Gas)
- Set Switch 43: Set
- Comment: ''

## Trigger 172

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 1 brings At least 1 × Zerg Zergling to Location 203
- Player 2 brings At least 1 × Zerg Zergling to Location 204
- Player 3 brings At least 1 × Zerg Zergling to Location 206
- Player 4 brings At least 1 × Zerg Zergling to Location 205
- Player 12 brings At least 1 × Zerg Spire to Location 207

Actions:

- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 79
- Kill All × Zerg Spire owned by Player 12 at Location 207
- Kill All × Men owned by Player 12 at Location 207
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 43 클리어! 스타게이트에서 1, 2, 3, 4가 되는 유닛들에 서면 됩니다'
- Set Switch 44: Set
- Comment: ''

## Trigger 173

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Player 1 brings At least 1 × Zerg Zergling to Location 208
- Player 2 brings At least 1 × Zerg Zergling to Location 210
- Player 3 brings At least 1 × Zerg Zergling to Location 211
- Player 4 brings At least 1 × Zerg Zergling to Location 209
- Player 12 brings At least 1 × Mineral Field (Type 2) to Location 212

Actions:

- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 80
- Kill All × Mineral Field (Type 2) owned by Player 12 at Location 212
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 44 클리어! 10의 자릿 수가 해당 플레이어를 뜻합니다'
- Set Switch 45: Set
- Comment: ''

## Trigger 174

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Force 1 brings At least 4 × Zerg Zergling to Location 213
- Player 12 brings At least 1 × Protoss Templar Archives to Location 214

Actions:

- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 81
- Kill All × Protoss Templar Archives owned by Player 12 at Location 214
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 45 클리어! 계속 함께 다니셨다면 깨셨을 것입니다'
- Set Switch 46: Set
- Set Switch 99: Set
- Comment: ''

## Trigger 175

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Deaths(Player 7, At least 1, Unclean One)

Actions:

- Set Deaths(Player 7, Set to 0, Unclean One)
- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 82
- Kill All × Zerg Nydus Canal owned by Player 12 at Location 219
- Kill All × Zerg Greater Spire owned by Player 12 at Location 219
- Set Resources(Force 1, Set to 0 Minerals)
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 46 클리어! 거꾸로 보니 답이 보이는군요'
- Set Switch 47: Set
- Comment: ''

## Trigger 176

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Deaths(Player 7, At least 1, Ursadon)

Actions:

- Set Deaths(Player 7, Set to 0, Ursadon)
- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 83
- Kill All × Zerg Nydus Canal owned by Player 12 at Location 220
- Kill All × Zerg Greater Spire owned by Player 12 at Location 220
- Set Resources(Force 1, Set to 0 Minerals)
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 47 클리어! ㄷ한자 7, 8, 1 입니다'
- Set Switch 48: Set
- Comment: ''

## Trigger 177

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Deaths(Player 7, At least 1, Vespene Geyser)

Actions:

- Set Deaths(Player 7, Set to 0, Vespene Geyser)
- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 84
- Kill All × Zerg Nydus Canal owned by Player 12 at Location 223
- Kill All × Zerg Greater Spire owned by Player 12 at Location 223
- Set Resources(Force 1, Set to 0 Minerals)
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 48 클리어! 조금 어려운 복면산입니다'
- Set Switch 49: Set
- Comment: ''

## Trigger 178

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Deaths(Player 7, At least 1, Terran Vulture)

Actions:

- Set Deaths(Player 7, Set to 0, Terran Vulture)
- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Terran Supply Depot owned by Player 12 at Location 85
- Kill All × Zerg Nydus Canal owned by Player 12 at Location 226
- Kill All × Zerg Greater Spire owned by Player 12 at Location 226
- Set Resources(Force 1, Set to 0 Minerals)
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 49 클리어! 888은 000 두 개가 합쳐진 수입니다'
- Set Switch 50: Set
- Comment: ''

## Trigger 179

- Owners: Force 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Deaths(Player 7, At least 1, Spider Mine)

Actions:

- Set Deaths(Player 7, Set to 0, Spider Mine)
- Play WAV 'staredit\\wav\\JoHap.wav'
- Kill All × Zerg Nydus Canal owned by Player 12 at Location 227
- Kill All × Zerg Greater Spire owned by Player 12 at Location 227
- Set Resources(Force 1, Set to 0 Minerals)
- Remove all Map Revealer owned by Player 7
- Display Text Message: '스테이지 50 클리어! 16을 16진수, 15진수, 14진수... 로 나타낸 것입니다'
- Set Switch 51: Set
- Comment: ''

## Trigger 180

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Protoss Scout to Location 90

Actions:

- Kill 1 × Protoss Scout owned by Current Player at Location 90
- Set Resources(Current Player, Add 1 Minerals)
- Play WAV 'staredit\\wav\\typing.wav'
- Preserve Trigger

## Trigger 181

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Protoss Carrier to Location 90

Actions:

- Kill 1 × Protoss Carrier owned by Current Player at Location 90
- Set Resources(Current Player, Add 10 Minerals)
- Play WAV 'staredit\\wav\\typing.wav'
- Preserve Trigger

## Trigger 182

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Protoss Arbiter to Location 90

Actions:

- Kill 1 × Protoss Arbiter owned by Current Player at Location 90
- Set Resources(Current Player, Add 100 Minerals)
- Play WAV 'staredit\\wav\\typing.wav'
- Preserve Trigger

## Trigger 183

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Protoss Corsair to Location 90

Actions:

- Kill 1 × Protoss Corsair owned by Current Player at Location 90
- Set Resources(Current Player, Set to 0 Minerals)
- Play WAV 'staredit\\wav\\typing.wav'
- Preserve Trigger

## Trigger 184

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 86
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 88

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 86 → Location 88
- Display Text Message: '478963 = 7 7412365 = 6 963 + 9874563 × 745693 = ?'
- Play WAV 'staredit\\wav\\typing.wav'
- Preserve Trigger

## Trigger 185

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 87
- Current Player accumulates At least 38 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 88

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 87 → Location 88
- Display Text Message: '틀렸습니다!'
- Set Resources(Current Player, Set to 0 Minerals)
- Preserve Trigger

## Trigger 186

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 87
- Current Player accumulates At most 36 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 88

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 87 → Location 88
- Display Text Message: '틀렸습니다!'
- Set Resources(Current Player, Set to 0 Minerals)
- Preserve Trigger

## Trigger 187

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 87
- Current Player accumulates Exactly 37 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 88

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 87 → Location 88
- Set Deaths(Player 7, Add 1, Terran Nuclear Silo)

## Trigger 188

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 97
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 101

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 97 → Location 101
- Display Text Message: '124 137 250 373 516 839 ???'
- Play WAV 'staredit\\wav\\typing.wav'
- Preserve Trigger

## Trigger 189

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 100
- Current Player accumulates At least 373 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 101

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 100 → Location 101
- Display Text Message: '틀렸습니다!'
- Set Resources(Current Player, Set to 0 Minerals)
- Preserve Trigger

## Trigger 190

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 100
- Current Player accumulates At most 371 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 101

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 100 → Location 101
- Display Text Message: '틀렸습니다!'
- Set Resources(Current Player, Set to 0 Minerals)
- Preserve Trigger

## Trigger 191

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 100
- Current Player accumulates Exactly 372 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 101

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 100 → Location 101
- Set Deaths(Player 7, Add 1, Terran Physics Lab)

## Trigger 192

- Owners: Player 5
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Protoss Archon to Location 104

Actions:

- Order Protoss Archon owned by Current Player at Location 104: Move to Location 103
- Preserve Trigger

## Trigger 193

- Owners: Player 5
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Protoss Archon to Location 103

Actions:

- Order Protoss Archon owned by Current Player at Location 103: Move to Location 104
- Preserve Trigger

## Trigger 194

- Owners: Player 5
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Devourer to Location 118

Actions:

- Order Zerg Devourer owned by Current Player at Location 118: Move to Location 119
- Preserve Trigger

## Trigger 195

- Owners: Player 5
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Devourer to Location 119

Actions:

- Order Zerg Devourer owned by Current Player at Location 119: Move to Location 118
- Preserve Trigger

## Trigger 196

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 104
- Switch 16 is Set

Actions:

- Give All × Protoss Archon at Location 105: Player 5 → Current Player
- Display Text Message: '공정한 대결을 추구하십시요!'
- Play WAV 'staredit\\wav\\typing.wav'

## Trigger 197

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 121
- Switch 18 is Set
- Switch 27 is Cleared

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 121 → Location 120
- Display Text Message: '인생의 진리를 깨닫고 싶다면 그 때 오게나...'
- Play WAV 'staredit\\wav\\typing.wav'
- Preserve Trigger

## Trigger 198

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 141
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 143

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 141 → Location 143
- Display Text Message: 'SPE'
- Play WAV 'staredit\\wav\\typing.wav'
- Preserve Trigger

## Trigger 199

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 142
- Current Player accumulates At least 393 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 143

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 142 → Location 143
- Display Text Message: '틀렸습니다!'
- Set Resources(Current Player, Set to 0 Minerals)
- Preserve Trigger

## Trigger 200

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 142
- Current Player accumulates At most 391 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 143

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 142 → Location 143
- Display Text Message: '틀렸습니다!'
- Set Resources(Current Player, Set to 0 Minerals)
- Preserve Trigger

## Trigger 201

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 142
- Current Player accumulates Exactly 392 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 143

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 142 → Location 143
- Set Deaths(Player 7, Add 1, Terran Refinery)

## Trigger 202

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 145
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 146

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 145 → Location 146
- Display Text Message: '1+2×3 = 33 3×6+19 = 54 4+2×5+1 = ?'
- Play WAV 'staredit\\wav\\typing.wav'
- Preserve Trigger

## Trigger 203

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 144
- Current Player accumulates At least 67 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 146

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 144 → Location 146
- Display Text Message: '틀렸습니다!'
- Set Resources(Current Player, Set to 0 Minerals)
- Preserve Trigger

## Trigger 204

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 144
- Current Player accumulates At most 65 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 146

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 144 → Location 146
- Display Text Message: '틀렸습니다!'
- Set Resources(Current Player, Set to 0 Minerals)
- Preserve Trigger

## Trigger 205

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 144
- Current Player accumulates Exactly 66 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 146

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 144 → Location 146
- Set Deaths(Player 7, Add 1, Terran Science Facility)

## Trigger 206

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 148
- Switch 25 is Set

Actions:

- Give All × Bengalaas at Anywhere (L64): Player 12 → Current Player
- Display Text Message: '마스터 키를 획득하셨습니다.'
- Play WAV 'staredit\\wav\\typing.wav'

## Trigger 207

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 121
- Switch 27 is Set

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 121 → Location 120
- Display Text Message: '인생의 진리는 無 라네'
- Set Invincibility: Disable for Protoss Robotics Facility owned by Player 12 at Location 151
- Play WAV 'staredit\\wav\\typing.wav'
- Preserve Trigger

## Trigger 208

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 162
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 163

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 162 → Location 163
- Display Text Message: 'Psi used = 0 Psi total + Psi max = ?'
- Play WAV 'staredit\\wav\\typing.wav'
- Preserve Trigger

## Trigger 209

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 161
- Current Player accumulates At least 225 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 163

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 161 → Location 163
- Display Text Message: '틀렸습니다!'
- Set Resources(Current Player, Set to 0 Minerals)
- Preserve Trigger

## Trigger 210

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 161
- Current Player accumulates At most 223 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 163

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 161 → Location 163
- Display Text Message: '틀렸습니다!'
- Set Resources(Current Player, Set to 0 Minerals)
- Preserve Trigger

## Trigger 211

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 161
- Current Player accumulates Exactly 224 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 163

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 161 → Location 163
- Set Deaths(Player 7, Add 1, Terran Science Vessel)

## Trigger 212

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 164
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 166

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 164 → Location 166
- Display Text Message: '1부터 하다보면...'
- Play WAV 'staredit\\wav\\typing.wav'
- Preserve Trigger

## Trigger 213

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 165
- Current Player accumulates At least 1 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 166

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 165 → Location 166
- Display Text Message: '틀렸습니다!'
- Set Resources(Current Player, Set to 0 Minerals)
- Preserve Trigger

## Trigger 214

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 165
- Current Player accumulates Exactly 0 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 166

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 165 → Location 166
- Set Deaths(Player 7, Add 1, Terran Siege Tank (Siege Mode))

## Trigger 215

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 168
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 169

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 168 → Location 169
- Display Text Message: 'T + I = 13 EYY + QUP = ?'
- Play WAV 'staredit\\wav\\typing.wav'
- Preserve Trigger

## Trigger 216

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 167
- Current Player accumulates At least 537 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 169

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 167 → Location 169
- Display Text Message: '틀렸습니다!'
- Set Resources(Current Player, Set to 0 Minerals)
- Preserve Trigger

## Trigger 217

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 167
- Current Player accumulates At most 535 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 169

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 167 → Location 169
- Display Text Message: '틀렸습니다!'
- Set Resources(Current Player, Set to 0 Minerals)
- Preserve Trigger

## Trigger 218

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 167
- Current Player accumulates Exactly 536 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 169

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 167 → Location 169
- Set Deaths(Player 7, Add 1, Terran Starport)

## Trigger 219

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 171
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 172

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 171 → Location 172
- Display Text Message: 'sanctuary'
- Play WAV 'staredit\\wav\\typing.wav'
- Preserve Trigger

## Trigger 220

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 170
- Current Player accumulates At least 212 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 172

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 170 → Location 172
- Display Text Message: '틀렸습니다!'
- Set Resources(Current Player, Set to 0 Minerals)
- Preserve Trigger

## Trigger 221

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 170
- Current Player accumulates At most 210 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 172

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 170 → Location 172
- Display Text Message: '틀렸습니다!'
- Set Resources(Current Player, Set to 0 Minerals)
- Preserve Trigger

## Trigger 222

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 170
- Current Player accumulates Exactly 211 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 172

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 170 → Location 172
- Set Deaths(Player 7, Add 1, Terran Valkyrie)

## Trigger 223

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 174
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 175

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 174 → Location 175
- Display Text Message: '- 철수 신상정보 - 생년월일 : x년 2월 7일 성별 : 남성 직업 : S기업 회사원 결혼 유무 : 유 철수는 1981년에 서울대학교 2학년이였다. 철수가 태어난 년도는? (단, 철수는 한 번에 대학에 붙으며 군대는 면제되었다)'
- Play WAV 'staredit\\wav\\typing.wav'
- Preserve Trigger

## Trigger 224

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 173
- Current Player accumulates At least 1963 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 175

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 173 → Location 175
- Display Text Message: '틀렸습니다!'
- Set Resources(Current Player, Set to 0 Minerals)
- Preserve Trigger

## Trigger 225

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 173
- Current Player accumulates At most 1961 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 175

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 173 → Location 175
- Display Text Message: '틀렸습니다!'
- Set Resources(Current Player, Set to 0 Minerals)
- Preserve Trigger

## Trigger 226

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 173
- Current Player accumulates Exactly 1962 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 175

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 173 → Location 175
- Set Deaths(Player 7, Add 1, Tom Kazansky)

## Trigger 227

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 177
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 178

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 177 → Location 178
- Display Text Message: '2011년 그 날, 70만, 그들의 모든 것이 끝난다.'
- Play WAV 'staredit\\wav\\typing.wav'
- Preserve Trigger

## Trigger 228

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 176
- Current Player accumulates At least 1111 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 178

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 176 → Location 178
- Display Text Message: '틀렸습니다!'
- Set Resources(Current Player, Set to 0 Minerals)
- Preserve Trigger

## Trigger 229

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 176
- Current Player accumulates At most 1109 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 178

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 176 → Location 178
- Display Text Message: '틀렸습니다!'
- Set Resources(Current Player, Set to 0 Minerals)
- Preserve Trigger

## Trigger 230

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 176
- Current Player accumulates Exactly 1110 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 178

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 176 → Location 178
- Set Deaths(Player 7, Add 1, Torrasque)

## Trigger 231

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 180
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 181

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 180 → Location 181
- Display Text Message: 'A row B column A × B × B = ?'
- Play WAV 'staredit\\wav\\typing.wav'
- Preserve Trigger

## Trigger 232

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 179
- Current Player accumulates At least 65 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 181

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 179 → Location 181
- Display Text Message: '틀렸습니다!'
- Set Resources(Current Player, Set to 0 Minerals)
- Preserve Trigger

## Trigger 233

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 179
- Current Player accumulates At most 63 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 181

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 179 → Location 181
- Display Text Message: '틀렸습니다!'
- Set Resources(Current Player, Set to 0 Minerals)
- Preserve Trigger

## Trigger 234

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 179
- Current Player accumulates Exactly 64 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 181

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 179 → Location 181
- Set Deaths(Player 7, Add 1, Zerg Ultralisk)

## Trigger 235

- Owners: All Players
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Force 1 brings At least 1 × Bengalaas to Location 147
- Player 12 brings At least 1 × Terran Supply Depot to Location 148
- Switch 25 is Set

Actions:

- Set Invincibility: Disable for Bengalaas owned by All Players at Anywhere (L64)

## Trigger 236

- Owners: Player 5
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Force 1 brings At least 1 × Zerg Zergling to Location 123
- Player 5 brings At most 0 × Zerg Scourge to Location 122
- Player 12 brings At least 1 × Terran Supply Depot to Location 54

Actions:

- Create 1 × Zerg Scourge for Player 5 at Location 123 with CUWP slot 1
- Order Zerg Scourge owned by Player 5 at Location 123: Move to Location 124
- Preserve Trigger

## Trigger 237

- Owners: Player 5
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Force 1 brings At least 1 × Zerg Zergling to Location 124
- Player 5 brings At least 1 × Zerg Scourge to Location 124

Actions:

- Order Zerg Scourge owned by Player 5 at Location 124: Move to Location 125
- Preserve Trigger

## Trigger 238

- Owners: Player 5
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Force 1 brings At most 0 × Zerg Zergling to Location 124
- Player 5 brings At least 1 × Zerg Scourge to Location 124

Actions:

- Kill All × Zerg Scourge owned by Player 5 at Location 122
- Preserve Trigger

## Trigger 239

- Owners: Player 5
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Force 1 brings At least 1 × Zerg Zergling to Location 125
- Player 5 brings At least 1 × Zerg Scourge to Location 125

Actions:

- Order Zerg Scourge owned by Player 5 at Location 125: Move to Location 126
- Preserve Trigger

## Trigger 240

- Owners: Player 5
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Force 1 brings At most 0 × Zerg Zergling to Location 125
- Player 5 brings At least 1 × Zerg Scourge to Location 125

Actions:

- Kill All × Zerg Scourge owned by Player 5 at Location 122
- Preserve Trigger

## Trigger 241

- Owners: Player 5
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Force 1 brings At least 1 × Zerg Zergling to Location 126
- Player 5 brings At least 1 × Zerg Scourge to Location 126

Actions:

- Order Zerg Scourge owned by Player 5 at Location 126: Move to Location 127
- Preserve Trigger

## Trigger 242

- Owners: Player 5
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Force 1 brings At most 0 × Zerg Zergling to Location 126
- Player 5 brings At least 1 × Zerg Scourge to Location 126

Actions:

- Kill All × Zerg Scourge owned by Player 5 at Location 122
- Preserve Trigger

## Trigger 243

- Owners: Player 5
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Force 1 brings At least 1 × Zerg Zergling to Location 127
- Player 5 brings At least 1 × Zerg Scourge to Location 127

Actions:

- Order Zerg Scourge owned by Player 5 at Location 127: Move to Location 128
- Preserve Trigger

## Trigger 244

- Owners: Player 5
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Force 1 brings At most 0 × Zerg Zergling to Location 127
- Player 5 brings At least 1 × Zerg Scourge to Location 127

Actions:

- Kill All × Zerg Scourge owned by Player 5 at Location 122
- Preserve Trigger

## Trigger 245

- Owners: Player 5
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Force 1 brings At least 1 × Zerg Zergling to Location 128
- Player 5 brings At least 1 × Zerg Scourge to Location 128

Actions:

- Order Zerg Scourge owned by Player 5 at Location 128: Move to Location 129
- Preserve Trigger

## Trigger 246

- Owners: Player 5
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Force 1 brings At most 0 × Zerg Zergling to Location 128
- Player 5 brings At least 1 × Zerg Scourge to Location 128

Actions:

- Kill All × Zerg Scourge owned by Player 5 at Location 122
- Preserve Trigger

## Trigger 247

- Owners: Player 5
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Force 1 brings At least 1 × Zerg Zergling to Location 129
- Player 5 brings At least 1 × Zerg Scourge to Location 129

Actions:

- Order Zerg Scourge owned by Player 5 at Location 129: Move to Location 54
- Preserve Trigger

## Trigger 248

- Owners: Player 5
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Force 1 brings At most 0 × Zerg Zergling to Location 129
- Player 5 brings At least 1 × Zerg Scourge to Location 129

Actions:

- Kill All × Zerg Scourge owned by Player 5 at Location 122
- Preserve Trigger

## Trigger 249

- Owners: Player 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 192

Actions:

- Give All × Flag at Location 196: Current Player → Player 12

## Trigger 250

- Owners: Player 2
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 195

Actions:

- Give All × Flag at Location 196: Current Player → Player 12

## Trigger 251

- Owners: Player 3
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 194

Actions:

- Give All × Flag at Location 196: Current Player → Player 12

## Trigger 252

- Owners: Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 193

Actions:

- Give All × Flag at Location 196: Current Player → Player 12

## Trigger 253

- Owners: Player 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 198
- Player 12 brings At least 1 × Zerg Cerebrate to Location 202

Actions:

- Set Resources(Current Player, Set to 1 Gas)
- Preserve Trigger

## Trigger 254

- Owners: Player 1
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At most 0 × Zerg Zergling to Location 198
- Player 12 brings At least 1 × Zerg Cerebrate to Location 202

Actions:

- Set Resources(Current Player, Set to 0 Gas)
- Preserve Trigger

## Trigger 255

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 218
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 219

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 218 → Location 219
- Display Text Message: '868 668 006 ???'
- Play WAV 'staredit\\wav\\typing.wav'
- Preserve Trigger

## Trigger 256

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 217
- Current Player accumulates At least 107 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 219

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 217 → Location 219
- Display Text Message: '틀렸습니다!'
- Set Resources(Current Player, Set to 0 Minerals)
- Preserve Trigger

## Trigger 257

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 217
- Current Player accumulates At most 105 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 219

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 217 → Location 219
- Display Text Message: '틀렸습니다!'
- Set Resources(Current Player, Set to 0 Minerals)
- Preserve Trigger

## Trigger 258

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 217
- Current Player accumulates Exactly 106 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 219

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 217 → Location 219
- Set Deaths(Player 7, Add 1, Unclean One)

## Trigger 259

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 222
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 220

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 222 → Location 220
- Display Text Message: '×÷＋'
- Play WAV 'staredit\\wav\\typing.wav'
- Preserve Trigger

## Trigger 260

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 221
- Current Player accumulates At least 782 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 220

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 221 → Location 220
- Display Text Message: '틀렸습니다!'
- Set Resources(Current Player, Set to 0 Minerals)
- Preserve Trigger

## Trigger 261

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 221
- Current Player accumulates At most 780 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 220

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 221 → Location 220
- Display Text Message: '틀렸습니다!'
- Set Resources(Current Player, Set to 0 Minerals)
- Preserve Trigger

## Trigger 262

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 221
- Current Player accumulates Exactly 781 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 220

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 221 → Location 220
- Set Deaths(Player 7, Add 1, Ursadon)

## Trigger 263

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 225
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 223

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 225 → Location 223
- Display Text Message: 'F O R T Y + T E N + T E N = S I X T Y S I X = ?'
- Play WAV 'staredit\\wav\\typing.wav'
- Preserve Trigger

## Trigger 264

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 224
- Current Player accumulates At least 314 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 223

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 224 → Location 223
- Display Text Message: '틀렸습니다!'
- Set Resources(Current Player, Set to 0 Minerals)
- Preserve Trigger

## Trigger 265

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 224
- Current Player accumulates At most 312 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 223

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 224 → Location 223
- Display Text Message: '틀렸습니다!'
- Set Resources(Current Player, Set to 0 Minerals)
- Preserve Trigger

## Trigger 266

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 224
- Current Player accumulates Exactly 313 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 223

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 224 → Location 223
- Set Deaths(Player 7, Add 1, Vespene Geyser)

## Trigger 267

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 216
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 226

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 216 → Location 226
- Display Text Message: '000 + 000 = ?'
- Play WAV 'staredit\\wav\\typing.wav'
- Preserve Trigger

## Trigger 268

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 215
- Current Player accumulates At least 889 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 226

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 215 → Location 226
- Display Text Message: '틀렸습니다!'
- Set Resources(Current Player, Set to 0 Minerals)
- Preserve Trigger

## Trigger 269

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 215
- Current Player accumulates At most 887 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 226

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 215 → Location 226
- Display Text Message: '틀렸습니다!'
- Set Resources(Current Player, Set to 0 Minerals)
- Preserve Trigger

## Trigger 270

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 215
- Current Player accumulates Exactly 888 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 226

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 215 → Location 226
- Set Deaths(Player 7, Add 1, Terran Vulture)

## Trigger 271

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 229
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 227

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 229 → Location 227
- Display Text Message: '10 11 12 13 14 15 16 17 20 22 24 31 100 ???'
- Play WAV 'staredit\\wav\\typing.wav'
- Preserve Trigger

## Trigger 272

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 228
- Current Player accumulates At least 122 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 227

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 228 → Location 227
- Display Text Message: '틀렸습니다!'
- Set Resources(Current Player, Set to 0 Minerals)
- Preserve Trigger

## Trigger 273

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 228
- Current Player accumulates At most 120 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 227

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 228 → Location 227
- Display Text Message: '틀렸습니다!'
- Set Resources(Current Player, Set to 0 Minerals)
- Preserve Trigger

## Trigger 274

- Owners: Player 1, Player 2, Player 3, Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 228
- Current Player accumulates Exactly 121 Minerals
- Player 12 brings At least 1 × Zerg Nydus Canal to Location 227

Actions:

- Move All × Zerg Zergling owned by Current Player: Location 228 → Location 227
- Set Deaths(Player 7, Add 1, Spider Mine)

## Trigger 275

- Owners: Player 2
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 199
- Player 12 brings At least 1 × Zerg Cerebrate to Location 202

Actions:

- Set Resources(Current Player, Set to 1 Gas)
- Preserve Trigger

## Trigger 276

- Owners: Player 2
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At most 0 × Zerg Zergling to Location 199
- Player 12 brings At least 1 × Zerg Cerebrate to Location 202

Actions:

- Set Resources(Current Player, Set to 0 Gas)
- Preserve Trigger

## Trigger 277

- Owners: Player 3
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 201
- Player 12 brings At least 1 × Zerg Cerebrate to Location 202

Actions:

- Set Resources(Current Player, Set to 1 Gas)
- Preserve Trigger

## Trigger 278

- Owners: Player 3
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At most 0 × Zerg Zergling to Location 201
- Player 12 brings At least 1 × Zerg Cerebrate to Location 202

Actions:

- Set Resources(Current Player, Set to 0 Gas)
- Preserve Trigger

## Trigger 279

- Owners: Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At least 1 × Zerg Zergling to Location 200
- Player 12 brings At least 1 × Zerg Cerebrate to Location 202

Actions:

- Set Resources(Current Player, Set to 1 Gas)
- Preserve Trigger

## Trigger 280

- Owners: Player 4
- Execution flags: `0x00000000`
- Current action index: 0

Conditions:

- Current Player brings At most 0 × Zerg Zergling to Location 200
- Player 12 brings At least 1 × Zerg Cerebrate to Location 202

Actions:

- Set Resources(Current Player, Set to 0 Gas)
- Preserve Trigger

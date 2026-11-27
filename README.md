# SkrzynkaGracza 1.2

Plugin na Paper/Spigot **1.20.4 i nowsze** (animacje w świecie używają ItemDisplay).
Skrzynka z losowymi nagrodami, menu główne, edytor nagród w oknie, 9 animacji otwierania do wyboru,
historia losowań i ogłoszenia rzadkich nagród.

## Budowanie
mvn package  ->  target/SkrzynkaGracza-1.2.0.jar  (wrzuć do plugins/). Java 17+, Maven.

## Animacje (gracz wybiera w oknie: /skrzynka animacje)
W oknie:
 1. Klasyczna ruletka  - poziomy pasek nagród
 2. Wodospad           - pionowa kolumna spada w dół
 3. Karuzela           - nagrody kręcą się dookoła skrzynki
 4. Podwójne koło      - dwa pierścienie w przeciwne strony
 5. Jednoręki bandyta  - trzy bębny stają po kolei
 6. Eliminacja         - odpadają kolejne nagrody, zostaje jedna
W świecie (przed graczem pojawia się skrzynia, a wokół niej krążą nagrody):
 7. Orbita             - nagrody krążą, przyspieszają i zwalniają
 8. Spirala            - nagrody wznoszą się helisą
 9. Wybuch             - nagrody rozlatują się i wracają do skrzyni
Prawy klik na animacji = podgląd bez klucza i bez nagrody.
Wybór zapisuje się w gracze.yml.

## Okno skrzynki (54 sloty)
- 0-44 nagrody ze stronami (strzałki 47 / 51)
- 45, 46 (lewy dolny róg): otwórz bez animacji
- 52, 53 (prawy dolny róg): otwórz z animacją (lewy klik) lub podgląd (prawy klik)
- 48 menu główne, 49 klucze, 50 wybór animacji

## Komendy
/skrzynka                       menu główne
/skrzynka otworz                od razu skrzynka
/skrzynka animacje              wybór animacji
/skrzynka historia              ostatnie losowania
/skrzynka edytor                edytor nagród (admin)
/skrzynka dodaj <szansa>        dodaje przedmiot z ręki (admin)
/skrzynka szansa <id> <szansa>  zmienia szansę (admin)
/skrzynka usun <id>             usuwa nagrodę (admin)
/skrzynka lista                 lista nagród (admin)
/skrzynka klucz <gracz> [ilość] daje klucz, Hak potykacza (admin)
/skrzynka reload                przeładowanie (admin)
/skrzynka pomoc                 klikalna pomoc

## config.yml
default-animation, broadcast-rare, rare-threshold-percent, history-size

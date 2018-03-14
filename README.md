# RHT_Logger
Projekt aplikacji mobilnej oraz systemu zbierającego wartości temperatury i poziomu wilgotności powietrza 

Pierwsza część  - oprogramowanie układu nRF51822 (ARM Cortex M0), który z określoną częstotliwością zbiera informacje na temat poziomu wilgotności powietrza oraz temperatury oraz gromadzi ich historię. 

Druga część - aplikacja mobilna łącząca się przez BLE z  mikrokontrolerem. 
1. Odbiera dane przez niego zebrane i wyświetla je na ekranie w formie listy, wraz z czasem ile minut upłynęło od wykonania danego pomiaru.
2. Umożliwia wykonanie bieżących pomiarów i ich odebranie i wyświetlenie.
3. Umożliwia zmianę częstotliwości wykonywania pomiarów przez mikrokontroler.
4. Umożliwia wyczyszczenie historii pomiarów zebranej przez mikrokontroler.

Aplikacja nie zapisuje żadnych danych - wszystkie zgromadzone są w pamięci mikrokontrolera.
Każda z funkcjonalności dostępna jest w formie przycisku wyzwalającego odpowiednią aktywność.
  
Ścieżka do projektu na nRF51822 - nRF5_SDK_11.0.0\examples\ble_peripheral\RHT_Logger - projekt w środowisku Keil µVision 5 IDE + programator J-Link EDU
Ścieżka do projektu aplikacji mobilnej - Android\RHT_Logger

Autor: Michał Potemski


# Concurrent tasks scheduler

## Clasa 'MyDispather'

Am implementat in functia 'addTask' din clasa MyDispather logica de impartire
a task-urilor intre hosts, in functie de algoritmul de planificare rulat la
momentul curent. Intrucat in cadrul fiecarui test se foloseste un singur
algoritm de planificare, am considerat impartirea pe cazuri o abordare buna.

Pentru algoritmul de planificare Round Robin:

Am adaugat la clasa un nou membru, variabila atomica de tip intreg 'lastHostID',
ce retine ID-ul host-ului catre care dispatcherul a trimis ultimul task -
initializata cu valoarea -1 pentru a sti cand task-ul curent este primul
planificat - caz in care il trimitem prin conventie la host 0.
Altfel, am folosit un operator unar cu ajutorul caruia am calculat noua valoare
pentru 'lastHostID' (practic, host-ul la care se trimite task-ul curent). Se 
efectueaza in acest fel o singura operatie atomica pentru a determina ID-ul.

Pentru algoritmul de planificare Shortest Queue:

Am folosit obiectul MyDispatcher pentru a sincroniza acest bloc de operatii
(lucru posibil deoarece fiecare test ruleaza un singur alg de planificare),
pentru a putea parcurge fiecare host si a vedea care are coada cu cel mai mic
numar de task-uri. Determinam minimul si trimitem task-ul catre acel host -
iar pentru ca totul este sincronizat la nivel de dispatcher ne asiguram ca nu 
adaugam intre timp alte task-uri la cozile host-ilor si ajungem la un posibil
rezultat eronat (este posibil doar ca pentru unii hosts sa se micsoreze numarul
de task-uri din coada in timp ce se determina minimul, dar acest lucru nu ne 
afecteaza substantial).

Pentru algoritmul de planificare Size Interval Task Assignment:

Nu avem nevoie de sincronizarea operatiilor deoarece task-urile se vor adauga
la host-ul corespunzator tipului fiecaruia, iar acolo ordinea va fi stabilita
automat de coada de prioritati. Am folosit astfel un switch pentru impartirea
task-urilor dupa tip.

Pentru algoritmul de planificare Least Work Left:

Am folosit obiectul MyDispatcher pentru a sincroniza blocul de operatii, ca 
si in cazul alg. Shortest Queue pentru a fi posibila parcurgerea tuturor hosts
si determinarea celui care are LWL cel mai mic (fara a risca sa adaugam in 
acest timp task-uri care sa creasca LWL). Cum pentru fiecare host putem
aproxima ca se efectueaza acelasi cantitate de procesare in timpul verificarii
noastre si nu adaugam task-uri noi la niciun host, putem fi siguri ca rezultatul
este cel corect.

## Clasa 'MyHost'

Am folosit pentru implementarea cozii de task-uri un obiect de tip
'PriorityBlockingQueue' pentru a asigura acces exclusiv atunci cand se
efectueaza operatii pe coada. Aceasta contine elemente de tip 'MyTuple', obiect
ce retine un obiect de tip Task, impreuna cu un intreg 'timeStamp', ce memoreaza
ordinea in care task-urile au ajuns in coada host-ului curent - pentru cazurile
in care avem doua sau mai multe task-uri cu aceeasi prioritate in coada si
avem nevoie de un criteriu de departajare.

Functia 'compareTo' din clasa 'MyTuple' se asigura ca ordinea in coada este
pastrata dupa cerintele enuntului.

De asemenea, am ales sa simulez executia unui task pe host prin functia
Thread.sleep() pe durata de executie ramasa, in functia run(), iar preemptarea
prin intreruperea executiei task-ului, atunci cand este necesar si posibil.
Astfel, executia are caracter continuu si introducem un overhead temporal minim.
Durata de executie este aproximata in cazul preemptarii.

Clasa contine mai multe alte variabile membru de tip atomic:

- 'workLeft' (AtomicLong)
    retine o aproximare pentru numarul de milisecunde de procesare ramase

- 'queueSize' (AtomicInteger)
    retine numarul de task-uri aflate in coada sau in procesare

- 'taskTimestamp' (AtomicInteger)
    initializat cu valoarea 0, reprezinta un contor global pentru task-urile
    venite in sistem. valoarea sa este plasata in obiectul 'MyTuple' impreuna
    cu task-ul si apoi incrementata


### Functia addTask():

Adaugam task-ul in coada, incrementam variabila ‘queueSize’ si adaugam durata
task-ului la variabila ‘workLeft’.

Apoi, verificam daca task-ul adaugat in coada preempteaza task-ul care ruleaza
la momentul curent pe host. Practic, verificam daca este nevoie sa intrerupem
executia task-ului curent de pe procesor pentru a lasa task-ul abia adaugat in
coada sa ruleze.
Pentru acest lucru, ne asiguram ca facem aceasta verificare doar in momentul in
care stim sigur ca un task ruleaza pe procesor. Pentru aceasta, folosim latch-ul
‘updateLatch’ si variabila ‘crtFinished’ - ambele folosite si in functia run().

Daca task-ul este preemtabil si are prioritate mai mica decat cel curent,
intrerupem task-ul si estimam cat timp a rulat, actualizam variabila ‘workLeft’
si durata ramasa de executie pentru task-ul preemptat. Daca mai are de executat,
il readaugam in coada. Altfel, il marcam drept finalizat.


### Functia run():

Ciclăm până când thread-ul ce ruleaza aceasta functie este intrerupt de functia
shutdown() si nu mai avem nimic de executat (coada este vida).

Anuntam functia shutdown() ca vrem sa scoatem un task din coada folosind latch-ul
shutdownLatch (care ne va intrerupe executia in cazul in care asteptam la o coada
vida), apoi preluam latch-ul updateLatch si scoatem task-ul cel mai prioritar din
coada si retinem timpul inceperii executiei.

Eliberam latch-ul si apelam Thread.sleep() pe durata de executie ramasa - timp in
care poate fi intrerupt de functia addTask.

La terminarea executiei, preluam din nou latch-ul updateLatch, unde actualizam
workLeft, queueSize si marcam task-ul drept finalizat.

Prin folosirea updateLatch si a boolean-ului 'crtFinished', vom sti mereu ca
incercam preemptarea in functia 'addTask' doar atunci cand ruleaza un task. 


### Functia getWorkLeft():

Preluam latch-ul ‘workLeftLatch’, iar daca un task ruleaza la momentul curent
estimam durata pe care a rulat, pentru a trimite catre dispatcher o valoare
corecta.

Folosim acest latch in toate locurile in care actualizam valoarea lui 'workLeft',
pentru a ne asigura ca nu scadem de mai multe ori durata de executie a unui
task. In functia run() ne asiguram ca variabila 'currentlyRunning' sa aiba
valoarea true doar atunci cand task-ul ruleaza, iar in functia 'addTask' setam
aceasta variabila pe false dupa intreruperea task-ului, impreuna cu actualizarea
lui 'workLeft'.

Evitam astfel probleme in cazul in care un task se termina / e intrerupt exact in
momentul in care dispatcher-ul apeleaza functia 'getWorkLeft'.


### Functia shutdown():

Dupa cum s-a explicat si mai sus, folosim shutdownLatch pentru a astepta ca
dimensiunea cozii sa fie 0 si sa putem intrerupe executia host-ului atunci cand
suntem siguri ca a terminat de rulat task-urile.


## Bibliografie:

https://docs.oracle.com/javase/8/docs/api/
https://mobylab.docs.crescdi.pub.ro/docs/parallelAndDistributed/laboratory4/
https://mobylab.docs.crescdi.pub.ro/docs/parallelAndDistributed/laboratory5/
https://mobylab.docs.crescdi.pub.ro/docs/parallelAndDistributed/laboratory6/

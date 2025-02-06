# Documentația Proiectului - Aplicație de Chat Web

## 1. Introducere
Această aplicație de chat web este dezvoltată utilizând **Spring Boot** pentru backend și **Thymeleaf** pentru frontend. Aplicația permite utilizatorilor să creeze conturi, să inițieze sesiuni de chat, să participe la conversații în camere publice și private, să trimită mesaje și fișiere, și să gestioneze parolele și autentificarea.

## 2. Tehnologii Utilizate
- **Backend:** Spring Boot 3.4.2, Java 23, OpenJDK 23
- **Frontend:** Thymeleaf, Bootstrap
- **Bază de date:** MySQL
- **Autentificare & Securitate:** Spring Security, JWT, BCrypt
- **Comunicație în timp real:** WebSockets cu STOMP
- **Stocare fișiere:** MultipartFile & Base64

## 3. Funcționalități Principale
### a) Autentificare și Înregistrare
- **Autentificare:** Implementată prin **Spring Security**, utilizatorii se pot autentifica folosind email-ul și parola.
- **Înregistrare:** Utilizatorii noi se pot înregistra prin intermediul formularului din `register.html`.
- **Gestionarea parolelor:** Schimbarea parolei prin `/change-password`.

### b) Chat în Timp Real
- **Camere de chat publice și private:** Utilizatorii pot comunica în camere publice sau pot crea camere private cu participanți specifici.
- **Mesagerie WebSocket:** Implementată cu STOMP și WebSockets pentru transmiterea mesajelor în timp real.
- **Tipuri de mesaje:** Mesaje standard, notificări de **JOIN** și **LEAVE**.
- **Istoric conversații:** Mesajele sunt salvate în baza de date și pot fi accesate ulterior.

### c) Transfer de Fișiere
- **Încărcare și descărcare fișiere:** Suport pentru atașarea și descărcarea fișierelor în mesaje.
- **Limită fișiere:** 50 MB per fișier.

### d) Gestionarea Camerei de Chat
- **Creare cameră nouă** `/chat/room`
- **Ștergere cameră** `/chat/room/{roomId}`
- **Redenumire cameră** `/chat/room/{roomId}`

### e) Securitate
- **Protecție CSRF:** Exceptată pentru WebSockets și upload fișiere.
- **Autorizare:** Doar utilizatorii autentificați pot accesa camerele de chat.
- **Politica de sesiuni:** Maxim 10 sesiuni simultane per utilizator.
- **Parole criptate:** Folosind BCryptPasswordEncoder.

## 4. Structura Proiectului

### a) Configurații
- `WebSocketConfig.java` - Configurează STOMP WebSocket.
- `SecurityConfig.java` - Setează securitatea HTTP.
- `WebSocketSecurityConfig.java` - Restricționează accesul la WebSocket.
- `PasswordConfig.java` - Configurează encoder-ul BCrypt pentru parole.

### b) Modele și Repozitoare
- `User.java` - Modelul utilizatorului.
- `ChatRoom.java` - Modelul camerei de chat.
- `ChatMessage.java` - Modelul mesajului.
- `UserRepository.java`, `ChatRoomRepository.java`, `ChatMessageRepository.java` - Repozitoare pentru gestionarea datelor în baza de date.

### c) Servicii
- `UserService.java` - Gestionarea utilizatorilor (înregistrare, schimbare parolă, autentificare).
- `ChatService.java` - Gestionarea camerelor de chat și a mesajelor.
- `FileStorageService.java` - Stocarea și recuperarea fișierelor atașate mesajelor.

### d) Controlere
- `AuthController.java` - Gestionarea autentificării și înregistrării.
- `UserController.java` - Gestionarea utilizatorilor disponibili.
- `ChatController.java` - Gestionarea camerelor de chat și a mesajelor.

### e) Pagini Frontend
- `login.html` - Pagina de autentificare.
- `register.html` - Pagina de înregistrare.
- `chat.html` - Interfața principală a aplicației de chat.

## 5. Instalare și Configurare

### a) Configurarea Bazei de Date
1. Configurați un server MySQL.
2. Creați o bază de date `webchat`.
3. Configurați `application.properties` pentru conexiunea MySQL.

### b) Pornirea Aplicației
1. Clonați repository-ul.
2. Executați `mvn clean install`.
3. Rulați aplicația cu `mvn spring-boot:run`.

## 6. Utilizare
1. Accesați `http://localhost:8080/register` pentru a crea un cont.
2. Conectați-vă pe `http://localhost:8080/login`.
3. Intrați în camerele de chat sau creați una nouă.
4. Trimiteți mesaje și atașați fișiere.
5. Schimbați parola sau deconectați-vă dacă este necesar.

## 7. Diagrama ER
![ER](https://github.com/user-attachments/assets/ca4999d6-d0bf-467f-897b-986790c92468)



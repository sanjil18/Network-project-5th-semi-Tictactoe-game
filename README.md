# Networked Tic-Tac-Toe (Java Socket + Swing)

A two-player network Tic-Tac-Toe game built with Java sockets for communication and Java Swing for the desktop UI.

## Project Structure

```text
networked-tictactoe/
├── pom.xml
└── src/main/java/com/tictactoe/
		├── Server.java
		└── Client.java
```

## Features

- Real-time 2-player gameplay over TCP sockets
- Dedicated server that manages turns, moves, and game state
- Swing GUI client with:
	- 3x3 interactive board
	- Turn status updates
	- Win/draw detection visuals
	- Restart request/confirmation flow
	- Running match stats (wins/losses/draws)

## Tech Stack

- Java 17
- Maven
- Java Swing (UI)
- Java Networking (`java.net` sockets)

## Prerequisites

- JDK 17 installed
- Maven installed and available in `PATH`

Check versions:

```bash
java -version
mvn -version
```

## How to Run

From the repository root:

```bash
cd networked-tictactoe
```

### 1) Start the Server

In terminal 1:

```bash
mvn exec:java -Dexec.mainClass="com.tictactoe.Server"
```

The server listens on port `12345` and prints available local IPv4 addresses.

### 2) Start Two Clients

In terminal 2 and terminal 3 (run once in each):

```bash
mvn exec:java -Dexec.mainClass="com.tictactoe.Client"
```

Each client will:

1. Ask for server IP (use `localhost` if running on same machine)
2. Ask for player name
3. Join as Player X or Player O

## Gameplay Notes

- Only the current player can make a valid move.
- Server validates moves and broadcasts state updates.
- After game end, either player can request a restart.
- Restart requires confirmation from the other player.

## Network Details

- Protocol: plain text messages over TCP
- Default port: `12345`
- Max players per game: `2`

## Build

Compile the project:

```bash
mvn clean compile
```

Package into JAR:

```bash
mvn clean package
```

## Troubleshooting

- **Client cannot connect**: verify server is running and IP/port are correct.
- **Firewall blocks connection**: allow Java on private network or open port `12345`.
- **Wrong Java version**: ensure Java 17 is active.

## License

This project currently does not define a license.
# FootballStock

### further improvement backend:

- readme content list and cleanup
- cleanup with decoders / encoders
- implement more test cases
- implement Integration Tests with local DynamoDb container (e.g. check optimistic locking conditions)
- architectural diagram
- design and add http endpoints
- add technical endpoints to check player memory and cache
- stream task to check all players from all user stats and send user event if price has changed
- read envs from cloud (aws credentials)
- create dynamoDb resources in cloud
- own instance for transfermarkt parser service - separate container
- authentication mechanism <- sth to study, separate table in dynamo?
- Future improvement - use MongoDb for json storage

### further improvement frontend:

- table for player search
- display player profile
- display user state
- button to buy/sell player

### API:

- users:
    - user state by user [GET]
    - buy stock for user [POST]
    - sell stock for user [POST]
    - user balance by user [GET]
    - user events by user [GET]
    - create new user [POST]

- players:
    - players search by string [GET]
    - player profile by id [GET]

- technical:
    - get all events [GET]
    - get all user states [GET]
    - db stats (number of records) [GET]
    - cache stats (number of records) [GET]

### DynamoDB Local

- use docker image:

  `docker pull amazon/dynamodb-local`

- run docker image (`-sharedDb` is i portant to share tables through all credentials/regions):

  `docker run -p 8000:8000 amazon/dynamodb-local -jar DynamoDBLocal.jar -inMemory -sharedDb`

- to create all tables locally from .json file definition run:

  ```aws dynamodb create-table --cli-input-json file://src/main/resources/playerProfileTable.json --endpoint-url http://localhost:8000```

  ```aws dynamodb create-table --cli-input-json file://src/main/resources/userGameStateTable.json --endpoint-url http://localhost:8000```

  ```aws dynamodb create-table --cli-input-json file://src/main/resources/eventTable.json --endpoint-url http://localhost:8000```

- to check whether tables has been created type:

  ```aws dynamodb list-tables --endpoint-url http://localhost:8000```

  ```aws dynamodb describe-table --table-name PlayerProfile --endpoint-url http://localhost:8000```

- to scan table:

  ```aws dynamodb scan --table-name PlayerProfile --endpoint-url http://localhost:8000```

### C4 diagram

```mermaid
    C4Context
    title Football Stock Game

    Boundary(Internet, "Public Internet") {
        Person_Ext(User, "Game User", "???")

        Boundary(FootballStock, "FootballStock") {
            Container_Boundary(transportLayer, "transportLayer") {
                Component(Http, "http endpoints", "id", "???")
                Component(Console, "console printer", "id", "???")
            }

            Container_Boundary(logicLayer, "logicLayer") {
                Component(GameEngine, "GameEngine", "id", "Process game logic")
            }

            Container_Boundary(serviceLayer, "serviceLayer") {
                Component(UserGameStateMemory, "UserGameStateMemory", "id", "description")
                Component(EventMemory, "EventMemory", "id", "description")
                Component(PlayerService, "PlayerService", "id", "description")
            }
        }

        Boundary(ResourcesLayer, "ResourcesLayer") {
            ComponentDb_Ext(DynamoDB, "DynamoDB - 3 tables", "???")
            Container(TransferMarktApi, "transfermarkt-api.vercel.app", "???")
        }
    }

    Rel(User, Http, "???")
    Rel(User, Console, "???")
    Rel(Http, GameEngine, "???")
    Rel(Console, GameEngine, "???")
    Rel(GameEngine, UserGameStateMemory, "???")
    Rel(GameEngine, EventMemory, "???")
    Rel(GameEngine, PlayerService, "???")
    Rel(UserGameStateMemory, DynamoDB, "???")
    Rel(EventMemory, DynamoDB, "???")
    Rel(PlayerService, DynamoDB, "???")
    Rel(PlayerService, TransferMarktApi, "???")
    UpdateLayoutConfig($c4ShapeInRow="3", $c4BoundaryInRow="3")

```

### Player Profile Sequence diagram

```mermaid
sequenceDiagram
    actor User
    participant PlayerService
    participant PlayerProfileCache
    participant PlayerProfileMemory
    participant PlayerProfileClient
    User ->> PlayerService: player profile domain object request
    PlayerService ->> PlayerProfileCache: player profile json request
    PlayerProfileCache -->> PlayerService: player profile json if present or
    alt cache first lookup
        PlayerProfileCache ->> PlayerProfileMemory: player profile json request
        PlayerProfileMemory ->> PlayerProfileCache: player profile json response
    else cache second lookup
        PlayerProfileCache ->> PlayerProfileClient: player profile json request
        par update memory
            PlayerProfileClient ->> PlayerProfileMemory: player profile json response
        and update cache
            PlayerProfileClient ->> PlayerProfileCache: player profile json response
        end
    end
    PlayerProfileCache ->> PlayerService: player profile json response
    PlayerService ->> User: player profile domain object response
```

### Player Search Sequence diagram

```mermaid
sequenceDiagram
    actor User
    participant PlayerService
    participant PlayerSearchCache
    participant PlayerSearchClient
    User ->> PlayerService: player search domain object request
    PlayerService ->> PlayerSearchCache: player search json request
    PlayerSearchCache -->> PlayerService: player search json if present or
    PlayerSearchCache ->> PlayerSearchClient: player search json request
    PlayerSearchClient ->> PlayerSearchCache: player search json response
    PlayerSearchCache ->> PlayerService: player search json response
    PlayerService ->> User: player search domain object response
```

### User Game State Sequence diagram

```mermaid
sequenceDiagram
    actor User
    participant GameState
    participant PlayerService
    participant EventMemory
    Note over User, EventMemory: Create new user game state
    User ->> GameState: create new user request
    GameState ->> User: new user created
    GameState -->> EventMemory: send event [INITIALIZE_GAME]
    Note over User, EventMemory: Buy player stock
    User ->> GameState: buy player stock request
    GameState ->> PlayerService: player profile request
    PlayerService ->> GameState: player profile response
    GameState ->> User: buy player stock response
    GameState -->> EventMemory: send event [BUY_PLAYER]
    Note over User, EventMemory: Sell player stock
    User ->> GameState: sell player stock request
    GameState ->> PlayerService: player profile request
    PlayerService ->> GameState: player profile response
    GameState ->> User: sell player stock response
    GameState -->> EventMemory: send event [SELL_PLAYER]
```

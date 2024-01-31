# FootballStock

### further improvement backend:

- readme content list and cleanup
- ~~add EventService and UserGameStateService~~
- cleanup with decoders / encoders
- implement more test cases
- implement Integration Tests with local DynamoDb container (e.g. check optimistic locking conditions)
- ~~architectural diagram~~
- design and add http endpoints
- add technical endpoints to check player memory and cache
- stream task to check all players from all user stats and send user event if price has changed
- read envs from cloud (aws credentials)
- create dynamoDb resources in cloud
- own instance for transfermarkt parser service - separate container
- authentication mechanism <- sth to study, separate table in dynamo?
- Future improvement - use MongoDb for json storage
- add scalaFix
- add endpoint for see other user portfolio 
- add logic to earn dividend when player from portfolio is playing (needed client for
  ```https://transfermarkt-api.vercel.app/players/{playerId}/stats``` 
 and some logic to compare stats. 1% player shares value profit for each 1h played)
- fetch market value history form client and display on frontend side

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

- or you can simply run the script:
  [dynamoDbLocal.sh](deployment/environments/local/dynamoDbLocal.sh)
  
  ```cd deployment/environments/local; ./dynamoDbLocal.sh```


### C4 diagram

```mermaid
    C4Context
    Boundary(Internet, "public Internet") {
        Boundary(users, "app users") {
            Person_Ext(User, "Game User", "Multiple users processing the game in parallel.")
            Person_Ext(Admin, "Administration User", "With access to technical and diagnostic endpoints.")
        }

        Deployment_Node(UI, "FootballStock App UI") {
            Container(UIWeb, "Web UI", "TypeScript, React", "Web frontend.")
            Container(UINative, "Native Mobile UI", "id", "Frontend for mobile devices.")
        }

        Deployment_Node(FootballStock, "FootballStock App Backend") {
            Boundary(communicationLayer, "communication layer") {
                Container(Http, "API", "Scala, Tapir, http4s", "Http endpoints.")
                Container(Console, "CLI", "Scala", "Command-line interface.")
            }

            Boundary(logicLayer, "logic layer") {
                Container(GameEngine, "GameEngine", "Scala, fs2, cats, cats-effect", "Process game logic using services underneath.")
            }

            Boundary(serviceLayer, "service layer") {
                Container(PlayerService, "Player Service", "id", "Processing player data.")
                Container(EventService, "Event Service", "id", "Processing game events.")
                Container(GameStateService, "Game State Service", "id", "Processing game state.")
            }

            Boundary(memoryLayer, "memory layer") {
                Container(PlayerMemory, "Player Memory", "scala", "Access to player data R/W.")
                Container(EventMemory, "Event Memory", "scala", "Access to game events R/W.")
                Container(GameStateMemory, "Game State Memory", "scala", "Access to game state R/W.")
            }
        }

        Boundary(resourcesLayer, "External Resources") {
            Component(TransferMarktParserApi, "transfermarkt parser API", "transfermarkt-api.vercel.app")
            Component(TransfermarktWeb, "transfermarkt.com", "transfermarkt.com webpage")
            Boundary(AWS, "AWS") {
                ComponentDb_Ext(PlayerProfileTable, "Player Profile Table", "AWS DynamoDB")
                ComponentDb_Ext(EventTable, "Event Table", "AWS DynamoDB")
                ComponentDb_Ext(GameStateTable, "Game State Table", "AWS DynamoDB")
            }
        }
    }

    Rel(User, UIWeb, "Uses", "HTTPS")
    Rel(User, UINative, "Uses", "HTTPS")
    Rel(Admin, Http, "Uses", "HTTPS")
    Rel(Admin, Console, "Uses")
    Rel(UIWeb, Http, "Uses", "HTTPS / WEBSOCKET")
    Rel(Http, GameEngine, "Uses")
    Rel(Console, GameEngine, "Uses")
    Rel(GameEngine, PlayerService, "Uses")
    Rel(GameEngine, GameStateService, "Uses")
    Rel(GameEngine, EventService, "Uses")
    Rel(PlayerService, PlayerMemory, "Uses")
    Rel(PlayerService, EventMemory, "Uses")
    Rel(GameStateService, GameStateMemory, "Uses")
    Rel(EventService, EventMemory, "Uses")
    BiRel(PlayerMemory, PlayerProfileTable, "Connect", "JSON/HTTPS")
    BiRel(GameStateMemory, GameStateTable, "Connect", "JSON/HTTPS")
    BiRel(EventMemory, EventTable, "Connect", "JSON/HTTPS")
    Rel(PlayerMemory, TransferMarktParserApi, "Uses", "JSON/HTTPS")
    Rel(TransferMarktParserApi, TransfermarktWeb, "Uses", "HTTPS")
    UpdateLayoutConfig($c4ShapeInRow="5", $c4BoundaryInRow="5")

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

# FootballStock

### plan backend:
~~- move playerCache from client to memory~~
- PlayerId as Long
~~- pure to delay~~
- replace println by log's
- TransfermarktClientConfig rename
- add console endpoints/messages to check player memory and cache
- create 2 streams to update players json periodically and combine them:
    1. Take from db all playerIds where:
    - player is active (not retired)
    - last update (from client json) was more than given time period
    2. Find last PlayerId in db and try to fetch next N players from client
    - if all failed - stop fetching
    - if any found - scan next N number
- cleanup existing tests
- new tests for playerProfile Client, cache and fetch streams
- get rid of .get in fetch players streams
- design and add http endpoints
- read envs from cloud (aws credentials)
- create dynamoDb client
- create dynamoDb resources
- own instance for transfermarkt parser service - separate container
- authentication mechanism <- sth to study, separate table in dynamo?
- architectural diagram

### plan frontend:
- table for player search
- display player profile
- display user state
- button to buy/sell player


### DynamoDB Local
- use docker image:

  `docker pull amazon/dynamodb-local`

- run docker image (`-sharedDb` is i portant to share tables through all credentials/regions):

  `docker run -p 8000:8000 amazon/dynamodb-local -jar DynamoDBLocal.jar -inMemory -sharedDb`

- to create all tables locally from .json file definition run:

```aws dynamodb create-table --cli-input-json file://src/main/resources/playerProfileTable.json --endpoint-url http://localhost:8000```

```???```

```???```

- to check whether tables has been created type:

```aws dynamodb list-tables --endpoint-url http://localhost:8000```

```aws dynamodb describe-table --table-name PlayerProfile  --endpoint-url http://localhost:8000```

- to scan table:

```aws dynamodb scan --table-name PlayerProfile --endpoint-url http://localhost:8000```
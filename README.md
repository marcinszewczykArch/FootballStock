# FootballStock

### plan backend:
- move playerCache from client to memory
- add console endpoints/messages to check player memory and cache
- create stream to update players json periodically / by trigger from console and endpoint
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

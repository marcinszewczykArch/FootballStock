FROM openjdk:11

ADD target/scala-**/FootballStock.jar app.jar

ENTRYPOINT ["java","-jar","/app.jar"]

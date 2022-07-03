# raft-es

[![Build Status](https://app.travis-ci.com/liakhandrii/raft-es.svg?branch=master)](https://app.travis-ci.com/liakhandrii/raft-es)
<img align="right" width="200" height="200" src="https://raft.github.io/logo/annie-solo.png">

Raft consensus algorithm written in Java

Raft is a consensus algorithm that is designed to be easy to understand. It's equivalent to Paxos in fault-tolerance and performance. The difference is that it's decomposed into relatively independent subproblems, and it cleanly addresses all major pieces needed for practical systems. We hope Raft will make consensus available to a wider audience, and that this wider audience will be able to develop a variety of higher quality consensus-based systems than are available today.

[What is Raft](https://raft.github.io/)

**This is just a toy implementation, don't use it in production**

## ▶️ Running & Testing
#### Running
```
./gradlew runLocal
```
This will let you to sit back and enjoy the beautiful operation of Raft. It simulates 5 nodes running, getting random client requests, and randomly getting knocked out.

```
./gradlew runHttp
```
A quick HTTP implementation, allows you to send requests directly to the nodes at localhost:8085-8089. It takes some time to start up, don't send requests before it starts or it will die.

#### Testing
```
./gradlew test
```
This will run two very different sets of tests: your ususal unit tests and live integration tests. Basically everything is tested in the unit tests, as it should be, but the live integration tests are a cool way to observe this project.

## 🌏 HTTP implementation
5 nodes are running on localhost:8085-8089, you can get and post simple entries. Non-leader nodes will redirect you to the leader node if you send a POST request, but won't redirect your GET requests, I made it so you can check the entries stored on all the nodes.
```
# Get the last stored entry
GET HOST/entry

# Get all the stored entries
GET HOST/entries

# Saves a new entry to memory
POST HOST/entries
body {"data": String}
```

## 💾 What's used in the project

[Gradle](https://gradle.org)

[JUnit](https://junit.org/junit5/)

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
./gradlew run
```
This will let you to sit back and enjoy the beautiful operation of Raft. It simulates 5 nodes running, getting random client requests, and randomly getting knocked out.

#### Testing
```
./gradlew test
```
This will run two very different sets of tests: your ususal unit tests and live integration tests. Basically everything is tested in the unit tests, as it should be, but the live integration tests are a cool way to observe this project.

## 🌏 HTTP implementation
HTTP implementation is coming.
```
# Get the last stored entry
GET HOST/entry

# Saves a new entry to memory
POST HOST/entry
body {"data": String}
```

## 💾 What's used in the project

[Gradle](https://gradle.org)

[JUnit](https://junit.org/junit5/)

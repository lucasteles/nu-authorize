# authorizer
Nu Bank Authorizer Code Challenger

This project simulates an account transaction authorizer. 
It maintains a state of an account, validate and apply transactions to it.

The state is in memory, so when applications restarts the state resets.

## Prerequisites

You need to install:

* [Clojure](https://clojure.org)
* [Leiningen](https://leiningen.org)
* [Java](https://www.oracle.com/technetwork/pt/java/index.html)


### Restore dependencies
```
lein deps
```

## Usage

To run the app just use the command bellow

```
lein run
```

this will open a stdin input loop that will receive the expected json input and show the result for each of them

There are a file with an exemple input data in the project directory root.
you can pass the data of the file direct to the stdin of the program using the command above:

```
lein run < input
```

### Docker

Other way to run the application is using a docker build, 
just run the commands above to build the image and run it

```sh
$ docker build -t nu-authorizer .
$ docker run -it --rm nu-authorizer
```


## Running the tests

To run all tests
```
lein test
```


## Implementation

### Assumptions

- The transactions will be entered ordered by time
- Will not have an transaction input before an account input
- Will not have invalid contracts on inputs 


### Design choices

#### Why I'm using Clojure

I know how FP works, but I've never really used Clojure before. I am pretty knowledgeable 
in F# and some Haskell, but as you use Clojure as a main language, I decided to study it.

#### State track

I choose to track the state of the app in a more pure and functional way
using a recursive loop with the state on the main program. 
Another option could be to use an Atom to simulate a database, 
maybe using the Component architecture suggested by [Stuart Sierra](https://github.com/stuartsierra/component)
but I thought it's would add more complexity than necessary for the actual 
requirements of the challenge. 

The use of an Atom to simulate a database would 
fit better if we need to go with a concurrent approach. 
So would be possible to guarantee the atomicity of the state change.

#### State definition

The base state of the program has an account and list of transactions.

The program loop only sets an account and never change it, the app will validate
the transaction and add it to the current state returning a new state with it.
Because of that, when we have to get the account with the correct available limit we need to
process all transactions and apply the discount amount of each of then on the account data.

But with this, it's easy to recreate the state and keep track of all changes, even making possible the rollback of a transaction without the need to change the account data.



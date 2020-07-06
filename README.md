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

this will open a stdin input loop that will receive the expected JSON input and show the result for each of it

There is a file with an example of input data in the project directory root.
you can pass the data of the file directly to the stdin of the program using the command bellow:

```
lein run < input
```

### Docker

Other way to run the application is using a docker build, 
just run the commands bellow to build the image and run it

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

- The transactions will be entered sorted by date and time
- Account creation will always happen before a transaction
- Input will always contain only valid data

### Design choices

#### Why I'm using Clojure

I know how FP works, but I've never really used Clojure before. I am pretty knowledgeable 
in F# and some Haskell, but as you use Clojure as a main language, I decided to study it.

#### Why I'm using Schema

I have more experience with statically typed programming languages.
So I like to have something that tells me if my data structures don't respect
the contract I want.

Because of that, I choose to use Plumatic Schema in this project, it gives 
me fast feedback for some mistakes even only at runtime. 

#### State track

I choose to track the state of the app in a more pure and functional way
using a recursive loop with the state on the main program. 
Another option could be to use an Atom to simulate a database, 
maybe using the Component architecture suggested by [Stuart Sierra](https://github.com/stuartsierra/component)
but I thought it would add more complexity than the necessary for the actual 
requirements of the challenge. 

The use of an Atom to simulate a database would 
fit better in a scenario where we need to handle concurrency. 
So it would be possible to guarantee the atomicity of the state change.

#### State definition

The base state of the program has an account and a list of transactions.

The program loop only sets an account and never changes it, the app will validate the transaction and add it to the current state returning a new state.

Because of that, when we have to get the account with the current available limit 
we need to process all transactions and apply them to the account data.

But with this, it's easy to recreate the state and keep track of all changes, even making possible the rollback of a transaction without the need to change the account data.

#### Transaction validation

The main goal is to build a clear and straight forward validation pipeline (with a Clojure thread)
You can see how it is in the `validate-transaction` function on the `logic.clj` file.

The `validate-transaction` receives the current state and the new transaction as 
arguments and apply each of the validation functions on it. Without the need to explicitly check if the validation goes wrong.

The ain of the validation is to add a representation of the error in an array inside a violations key in the state. 
So if the validations go well and we don't have any problem with the new transaction nothing is added to the violations array

This violations array must not be persisted in the main program state, the violations are scoped to the 
current transaction processing.

With this model is very easy to add a new validation, we only need to create a validator function and add it to the 
validation pipeline



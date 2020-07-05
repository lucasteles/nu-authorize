# authorizer
Nu Bank Authorizer Code Challenger

This project are made in Clojure and Lein 



## Usage

The basic usage is with 

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



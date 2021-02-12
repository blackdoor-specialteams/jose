# jose
[![](https://img.shields.io/codacy/grade/177db012dc7548be9143a7562cd1d4bd.svg?style=flat-square)](https://app.codacy.com/project/blackdoor/jose/dashboard)
[![Travis (.com)](https://img.shields.io/travis/com/blackdoor/jose.svg?style=flat-square)](https://travis-ci.com/blackdoor/jose)
[![Scaladoc](https://img.shields.io/badge/scaladoc-latest-blue.svg?style=flat-square)](https://blackdoor.github.io/jose/api/latest/black/door/jose/index.html)
[![Maven Central](https://img.shields.io/maven-central/v/black.door/jose_2.12.svg?style=flat-square)](https://mvnrepository.com/artifact/black.door/jose)
[![Gitter](https://img.shields.io/gitter/room/blackdoor/jose?style=flat-square)](https://gitter.im/blackdoor/jose?utm_source=share-link&utm_medium=link&utm_campaign=share-link)

Extensible JOSE library for Scala.

## Installation

The dependency is available on [Maven Central](https://mvnrepository.com/artifact/black.door/jose).

## Usage

Pretty simple: make a key, make something to sign, sign it.

```scala
val key = P256KeyPair.generate
val claims = Claims(
  sub = Some("my user"), 
  iss = Some("me"), 
  exp = Some(Instant.now.plus(1, ChronoUnit.DAYS))
)

val token = Jwt.sign(
  claims, 
  key.withAlg(Some("ES256"))
)

val errorOrJwt = Jwt
  .validate(token)
  .using(key, Check.iss(_ == "me"))
  .now
errorOrJwt.right.get.claims.sub // Some(my user)
```

### Selecting a JSON implementation

Currently supported JSON libraries:

* [x] [ninny JSON](https://mvnrepository.com/artifact/black.door/jose-json-ninny)
* [x] [Play JSON](https://mvnrepository.com/artifact/black.door/jose-json-play)
* [ ] [Json4s](http://json4s.org/)
* [x] [Circe](https://mvnrepository.com/artifact/black.door/jose-json-circe)

To add a JSON support, just import or mix in an implementation like `import black.door.jose.json.playjson.JsonSupport._`.

If your preferred library isn't supported, just implement `Mapper` implicits (or open an issue to request they be added).

### Async key resolution and validation checks

Frequently you will need to dynamically look up a new key from a keyserver based on a JWS header, 
or check a centralized cache to see if a token has been revoked.   
This is easy to do asynchronously by implementing `KeyResolver` or `JwtValidator.`  
`JwtValidator` is a partial function so you can easily chain both sync and async validations.  
`KeyResolver` allows you to return an error in the event that there was a specific reason a key could not be found 
(perhaps a key does exist, but it's only for encryption and this token is using it for signing).

### JWT validation DSL

There is a handy compile-safe DSL for JWT validation that allows you to indicate if you want to use unregistered claims, 
and if you want to evaluate synchronously or asynchronously. Its structure looks like this

```
Jwt
|__ .validate(compactJwt)
    |__ .using(keyResolver, etc...)
    |   |__ .now   // validates the JWT synchronously
    |   |__ .async // returns the validation result in a Future
    |__ .apply[UnregisteredClaims]
        |__ .using(keyResolver, etc...)
            |__ .now   // validates the JWT synchronously
            |__ .async // returns the validation result in a Future
```

So for example you could synchronously validate a JWT with some custom claims with

```scala
val customValidator = 
  JwtValidator.fromSync[MyCustomClaimsClass] { 
    case jwt if !jwt.claims.unregistered.thisTokenIsForAnAdmin => 
      "Token needs to be for an admin"
  }
  
Jwt.validate(compact)[MyCustomClaimsClass]
  .using(es256Key, customValidator)
  .now
```

> Not yet implemented:  
> * JWK serialization partly implemented
> * JWE
> * RSA signing (RSA signature verification is supported)
> * Less common key sizes for ECDSA
> * Custom JOSE header parameters (custom JWT claims are supported)

package black.door.jose.jwt

import java.security.KeyException
import java.util.concurrent.{Executors, TimeUnit}

import cats.data.{EitherT, OptionT}
import cats.implicits._
import black.door.jose.Mapper
import black.door.jose.jwa.{SignatureAlgorithm, SignatureAlgorithms}
import black.door.jose.jwk.Jwk
import black.door.jose.jws._
import black.door.jose.jwt.JwtValidator.JwtValidatorOps

import scala.collection.immutable.Seq
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

case class Jwt[HP](header: JwsHeader[HP], claims: Claims) extends Jws[HP, Claims] {
  def payload = claims
}

object Jwt {
  @throws[KeyException]
  def sign(claims: Claims, key: Jwk, algorithms: Seq[SignatureAlgorithm] = SignatureAlgorithms.all)
          (implicit headerSerializer: Mapper[JwsHeader[Unit], Array[Byte]], payloadSerializer: Mapper[Claims, Array[Byte]]) = {
    val alg = key.alg.getOrElse(throw new KeyException("Jwk must have a defined alg to use Jwt.sign. Alternatively, create a Jwt with an explicit JwsHeader."))
    Jwt(JwsHeader(alg, typ = Some("JWT"), kid = key.kid), claims).sign(key, algorithms)
  }

  /**
    *
    * @param compact
    * @param keyResolver
    * @param jwtValidator
    * @param fallbackJwtValidator A validator that runs if all the validations from jwtValidator pass.
    *                             By default this checks temporal claims.
    * @param signatureValidator
    * @param payloadDeserializer
    * @param headerDeserializer
    * @param ec
    * @tparam HP unregistered header parameters
    * @return
    */
  def validate[HP](
                compact: String,
                keyResolver: KeyResolver[HP, Claims],
                jwtValidator: JwtValidator[HP] = JwtValidator.empty,
                fallbackJwtValidator: JwtValidator[HP] = JwtValidator.defaultValidator(),
                algorithms: Seq[SignatureAlgorithm] = SignatureAlgorithms.all
              )
              (
                implicit payloadDeserializer: Mapper[Array[Byte], Claims],
                headerDeserializer: Mapper[Array[Byte], JwsHeader[HP]],
                ec: ExecutionContext
              ): Future[Either[String, Jwt[HP]]] = {
    EitherT(Jws.validate[HP, Claims](compact, keyResolver, algorithms))
      .flatMap { jws =>
        val jwt = Jwt(jws.header, jws.payload)
        OptionT(jwtValidator.orElse(fallbackJwtValidator).apply(jwt)).toLeft(jwt)
      }.value
  }

  private val sadSpasticLittleEc = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool)

  def validateSync(compact: String,
                   keyResolver: KeyResolver[Claims],
                   jwtValidator: JwtValidator = JwtValidator.empty,
                   fallbackJwtValidator: JwtValidator = JwtValidator.defaultValidator(),
                   algorithms: Seq[SignatureAlgorithm] = SignatureAlgorithms.all
                  )
                  (
                    implicit payloadDeserializer: Mapper[Array[Byte], Claims],
                    headerDeserializer: Mapper[Array[Byte], JwsHeader]
                  ) =
    Await.result(
      validate(compact, keyResolver, jwtValidator, fallbackJwtValidator, algorithms)
              (payloadDeserializer, headerDeserializer, sadSpasticLittleEc),
      Duration(1, TimeUnit.SECONDS)
    )
}

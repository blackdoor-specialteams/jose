package black.door.jose.jws


import com.nimbusds.jose.crypto.{MACSigner, MACVerifier}
import com.nimbusds.jose.{JWSAlgorithm, JWSHeader, JWSObject, Payload}
import org.scalatest.{EitherValues, Matchers, WordSpec}
import black.door.jose.Json._
import black.door.jose.jwk.HsJwk

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

class JwsSpec extends WordSpec with Matchers with EitherValues {
  "HS signatures" should {

    val hsKey = HsJwk.generate

    "sign correctly" in {
      val jws = Jws(JwsHeader("HS256"), "test data".getBytes)
      val compact = jws.sign(hsKey)

      val verifier = new MACVerifier(hsKey.k)
      JWSObject.parse(compact).verify(verifier) shouldBe true
    }

    "validate correctly" in {
      val signer = new MACSigner(hsKey.k)
      val payload = "test data"
      val jwsObj = new JWSObject(new JWSHeader(JWSAlgorithm.HS256), new Payload(payload))
      jwsObj.sign(signer)
      val compact = jwsObj.serialize

      val jws = Await.result(Jws.validate[String](compact, hsKey), Duration.Inf)
      jws.right.value.payload shouldBe payload
    }

    "reject invalid signatures" in {
      val jws = Jws(JwsHeader("HS256"), "test data".getBytes)
      val compact = jws.sign(hsKey)

      val parsedJws = Await.result(Jws.validate[String](compact, HsJwk.generate), Duration.Inf)
      parsedJws shouldBe 'left
    }
  }
}
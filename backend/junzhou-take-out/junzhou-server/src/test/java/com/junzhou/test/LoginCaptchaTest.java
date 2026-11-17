package com.junzhou.test;
import com.junzhou.service.LoginCaptchaService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import static org.junit.jupiter.api.Assertions.*;
class LoginCaptchaTest {
 @Test void imageAndSingleUse() {
  LoginCaptchaService service = new LoginCaptchaService();
  MockHttpSession session = new MockHttpSession();
  assertTrue(service.create(session).startsWith("data:image/png;base64,"));
  String answer = (String) session.getAttribute("login.captcha");
  service.verify(session, answer.toLowerCase());
  assertThrows(RuntimeException.class, () -> service.verify(session, answer));
 }
 @Test void rejectsWrongExpiredAndMissing() {
  LoginCaptchaService service = new LoginCaptchaService();
  MockHttpSession session = new MockHttpSession();
  service.create(session);
  assertThrows(RuntimeException.class, () -> service.verify(session, "wrong"));
  service.create(session);
  String answer = (String)session.getAttribute("login.captcha");
  session.setAttribute("login.captcha.expires", 0L);
  assertThrows(RuntimeException.class, () -> service.verify(session, answer));
  assertThrows(RuntimeException.class, () -> service.verify(null, answer));
 }
 @Test void defaultPasswordHashMatches() {
  assertTrue(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().matches("123456", "$2a$12$TtEZixOt6RQex3alq7Ze8OpV5tzQLwNFx1GW7jsd3vpmzlWIj3h9C"));
 }
}

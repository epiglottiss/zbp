package com.cus.zbp.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCrypt;
import com.cus.zbp.components.MailComponents;
import com.cus.zbp.user.entity.User;
import com.cus.zbp.user.model.PasswordResetInput;
import com.cus.zbp.user.model.UserInput;
import com.cus.zbp.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {
  @Mock
  private UserRepository userRepository;

  @Mock
  private MailComponents mailComponents;

  @InjectMocks
  private UserServiceImpl userService;

  @Test
  void successRegister() {
    // given
    String password = "password";
    String encPassword = BCrypt.hashpw(password, BCrypt.gensalt());

    UserInput userInput = UserInput.builder().email("not-exist-email@naver1.com").name("noname")
        .password(password).build();
    given(userRepository.findById(anyString())).willReturn(Optional.empty());

    given(userRepository.save(any())).willReturn(User.builder().email("not-exist-email@naver1.com")
        .name("noname").password(encPassword).registerDate(LocalDateTime.now()).emailAuth(false)
        .emailAuthKey("authkeyuuid").userStatus(User.MEMBER_STATUS_REQ).build());
    given(mailComponents.sendMail(anyString(), anyString(), anyString())).willReturn(true);
    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);

    // when
    boolean result = userService.register(userInput);
    // then
    verify(userRepository, times(1)).save(captor.capture());
    assertEquals(true, result);
    assertEquals("not-exist-email@naver1.com", captor.getValue().getEmail());
    assertEquals("noname", captor.getValue().getName());
    assertNotEquals("password", captor.getValue().getPassword());
    assertEquals(false, captor.getValue().isEmailAuth());
    assertEquals(User.MEMBER_STATUS_REQ, captor.getValue().getUserStatus());
  }

  @Test
  void failRegisterUserIsNotPresent() {
    // given
    UserInput userInput = UserInput.builder().email("not-exist-email@naver1.com").name("noname")
        .password("password").build();
    User user = User.builder().email("not-exist-email@naver1.com").name("noname")
        .password("password").build();
    given(userRepository.findById(anyString())).willReturn(Optional.of(user));
    // when
    boolean result = userService.register(userInput);
    // then
    assertEquals(false, result);
  }

  @Test
  void failRegisterSendMailFail() {
    // given
    String password = "password";
    String encPassword = BCrypt.hashpw(password, BCrypt.gensalt());

    UserInput userInput = UserInput.builder().email("not-exist-email@naver1.com").name("noname")
        .password(password).build();
    given(userRepository.findById(anyString())).willReturn(Optional.empty());

    given(userRepository.save(any())).willReturn(User.builder().email("not-exist-email@naver1.com")
        .name("noname").password(encPassword).registerDate(LocalDateTime.now()).emailAuth(false)
        .emailAuthKey("authkeyuuid").userStatus(User.MEMBER_STATUS_REQ).build());
    given(mailComponents.sendMail(anyString(), anyString(), anyString())).willReturn(false);
    // when
    boolean result = userService.register(userInput);
    // then
    assertEquals(false, result);
  }

  @Test
  void successEmailAuth() {
    // given
    String uuid = "auth-key";
    User user = User.builder().email("not-exist-email@naver1.com").name("noname")
        .password("password").emailAuthKey(uuid).emailAuth(false).build();
    given(userRepository.findByEmailAuthKey(anyString())).willReturn(Optional.of(user));
    given(userRepository.save(any())).willReturn(User.builder().email("not-exist-email@naver1.com")
        .name("noname").password("password").emailAuthKey("auth-key").emailAuth(true)
        .userStatus(User.MEMBER_STATUS_ING).emailAuthDate(LocalDateTime.now()).build());
    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);

    // when
    boolean result = userService.emailAuth(uuid);
    // then
    verify(userRepository, times(1)).save(captor.capture());
    assertEquals(true, result);
    assertEquals(User.MEMBER_STATUS_ING, captor.getValue().getUserStatus());
    assertEquals(true, captor.getValue().isEmailAuth());
    assertNotNull(captor.getValue().getEmailAuthDate());
  }

  @Test
  void failEmailAuthNotExistAuthKey() {
    // given
    given(userRepository.findByEmailAuthKey(anyString())).willReturn(Optional.empty());
    // when
    boolean result = userService.emailAuth("uuid");
    // then
    assertEquals(false, result);
  }

  @Test
  void failEmailAuthAlreadyAuthTrue() {
    // given
    String uuid = "auth-key";
    User user = User.builder().email("not-exist-email@naver1.com").name("noname")
        .password("password").emailAuthKey(uuid).emailAuth(true).build();
    given(userRepository.findByEmailAuthKey(anyString())).willReturn(Optional.of(user));

    // when
    boolean result = userService.emailAuth(uuid);
    // then
    assertEquals(false, result);
  }

  @Test
  void failLoadUserByUsername_usernameNotFound() {
    // given
    given(userRepository.findById(anyString())).willReturn(Optional.empty());
    // when
    Exception e = assertThrows(Exception.class, () -> userService.loadUserByUsername("any"));
    // then
    assertEquals("회원 정보가 존재하지 않습니다.", e.getMessage());
  }

  @Test
  void failLoadUserByUsername_MEMBER_STATUS_REQ() {
    // given
    User user =
        User.builder().email("not-exist-email@naver1.com").name("noname").password("password")
            .emailAuthKey("uuid").emailAuth(true).userStatus(User.MEMBER_STATUS_REQ).build();

    given(userRepository.findById(anyString())).willReturn(Optional.of(user));
    // when
    Exception e = assertThrows(Exception.class, () -> userService.loadUserByUsername("any"));
    // then
    assertEquals("이메일 인증 미완료 상태입니다.", e.getMessage());
  }

  @Test
  void failLoadUserByUsername_MEMBER_STATUS_STOP() {
    // given
    User user =
        User.builder().email("not-exist-email@naver1.com").name("noname").password("password")
            .emailAuthKey("uuid").emailAuth(true).userStatus(User.MEMBER_STATUS_STOP).build();

    given(userRepository.findById(anyString())).willReturn(Optional.of(user));
    // when
    Exception e = assertThrows(Exception.class, () -> userService.loadUserByUsername("any"));
    // then
    assertEquals("이용 정지된 아이디입니다.", e.getMessage());
  }

  @Test
  void failLoadUserByUsername_MEMBER_STATUS_WITHDRAW() {
    // given
    User user =
        User.builder().email("not-exist-email@naver1.com").name("noname").password("password")
            .emailAuthKey("uuid").emailAuth(true).userStatus(User.MEMBER_STATUS_WITHDRAW).build();

    given(userRepository.findById(anyString())).willReturn(Optional.of(user));
    // when
    Exception e = assertThrows(Exception.class, () -> userService.loadUserByUsername("any"));
    // then
    assertEquals("탈퇴한 아이디입니다.", e.getMessage());
  }

  @Test
  void successSendResetPassword() {
    // given
    User user = User.builder().email("not-exist-email@naver1.com").name("noname")
        .password("password").emailAuth(true).build();

    PasswordResetInput input = PasswordResetInput.builder().email("not-exist-email@naver1.com")
        .name("noname").id("uuid").password("password").build();
    given(userRepository.findByEmailAndName(anyString(), anyString()))
        .willReturn(Optional.of(user));

    given(userRepository.save(any()))
        .willReturn(User.builder().email("not-exist-email@naver1.com").build());
    given(mailComponents.sendMail(anyString(), anyString(), anyString())).willReturn(true);
    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);

    // when
    boolean result = userService.sendResetPassword(input);
    // then
    verify(userRepository, times(1)).save(captor.capture());
    assertEquals(true, result);
    assertNotNull(captor.getValue().getResetPasswordKey());
    assertNotNull(captor.getValue().getResetPasswordLimitDate());
  }

  @Test
  void failSendResetPassword() {
    // given
    PasswordResetInput input = PasswordResetInput.builder().email("not-exist-email@naver1.com")
        .name("noname").id("uuid").password("password").build();
    given(userRepository.findByEmailAndName(anyString(), anyString())).willReturn(Optional.empty());
    // when
    Exception e = assertThrows(Exception.class, () -> userService.sendResetPassword(input));
    // then
    assertEquals("회원 정보가 존재하지 않습니다.", e.getMessage());
  }

  @Test
  void successResetPassword() {
    // given
    User user = User.builder().email("not-exist-email@naver1.com").name("noname")
        .password("password").emailAuth(true).resetPasswordKey("resetKey")
        .resetPasswordLimitDate(LocalDateTime.now().plusHours(2)).build();

    given(userRepository.findByResetPasswordKey(anyString())).willReturn(Optional.of(user));
    given(userRepository.save(any()))
        .willReturn(User.builder().password(BCrypt.hashpw("password", BCrypt.gensalt()))
            .resetPasswordKey("").resetPasswordLimitDate(null).build());
    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);

    // when
    boolean result = userService.resetPassword("resetKey", "password");
    // then
    verify(userRepository, times(1)).save(captor.capture());
    assertEquals(true, result);
    assertNotEquals("password", captor.getValue().getPassword());
    assertEquals("", captor.getValue().getResetPasswordKey());
    assertNull(captor.getValue().getResetPasswordLimitDate());
  }

  @Test
  void failResetPassword_userNotFound() {
    // given
    given(userRepository.findByResetPasswordKey(anyString())).willReturn(Optional.empty());

    // when
    Exception e = assertThrows(Exception.class, () -> userService.resetPassword("any", "any"));
    // then
    assertEquals("회원 정보가 존재하지 않습니다.", e.getMessage());
  }

  @Test
  void failResetPassword_limitDateNull() {
    // given
    User user =
        User.builder().email("not-exist-email@naver1.com").name("noname").password("password")
            .emailAuth(true).resetPasswordKey("resetKey").resetPasswordLimitDate(null).build();

    given(userRepository.findByResetPasswordKey(anyString())).willReturn(Optional.of(user));

    // when
    Exception e = assertThrows(Exception.class, () -> userService.resetPassword("any", "any"));
    // then
    assertEquals("유효한 날짜가 아닙니다.", e.getMessage());
  }

  @Test
  void failResetPassword_limitDateExpired() {
    // given
    User user = User.builder().email("not-exist-email@naver1.com").name("noname")
        .password("password").emailAuth(true).resetPasswordKey("resetKey")
        .resetPasswordLimitDate(LocalDateTime.now().minusDays(1)).build();

    given(userRepository.findByResetPasswordKey(anyString())).willReturn(Optional.of(user));

    // when
    Exception e = assertThrows(Exception.class, () -> userService.resetPassword("any", "any"));
    // then
    assertEquals("유효한 날짜가 아닙니다.", e.getMessage());
  }

  @Test
  void successCheckResetPassword() {
    // given
    User user = User.builder().email("not-exist-email@naver1.com").name("noname")
        .password("password").emailAuth(true).resetPasswordKey("resetKey")
        .resetPasswordLimitDate(LocalDateTime.now().plusHours(2)).build();

    given(userRepository.findByResetPasswordKey(anyString())).willReturn(Optional.of(user));

    // when
    boolean result = userService.checkResetPassword("resetKey");
    // then
    assertEquals(true, result);
  }

  @Test
  void failCheckResetPassword_keyNotFound() {
    // given
    given(userRepository.findByResetPasswordKey(anyString())).willReturn(Optional.empty());

    // when
    boolean result = userService.checkResetPassword("resetKey");
    // then
    assertEquals(false, result);
  }

  @Test
  void failCheckResetPassword_limitDateNull() {
    // given
    User user =
        User.builder().email("not-exist-email@naver1.com").name("noname").password("password")
            .emailAuth(true).resetPasswordKey("resetKey").resetPasswordLimitDate(null).build();

    given(userRepository.findByResetPasswordKey(anyString())).willReturn(Optional.of(user));

    // when
    Exception e = assertThrows(Exception.class, () -> userService.checkResetPassword("resetKey"));
    // then
    assertEquals("유효한 날짜가 아닙니다.", e.getMessage());
  }

  @Test
  void failCheckResetPassword_limitDateExpired() {
    // given
    User user = User.builder().email("not-exist-email@naver1.com").name("noname")
        .password("password").emailAuth(true).resetPasswordKey("resetKey")
        .resetPasswordLimitDate(LocalDateTime.now().minusHours(2)).build();

    given(userRepository.findByResetPasswordKey(anyString())).willReturn(Optional.of(user));

    // when
    Exception e = assertThrows(Exception.class, () -> userService.checkResetPassword("resetKey"));
    // then
    assertEquals("유효한 날짜가 아닙니다.", e.getMessage());
  }
}

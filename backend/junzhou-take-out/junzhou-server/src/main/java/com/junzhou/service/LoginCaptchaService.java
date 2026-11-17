package com.junzhou.service;

import org.springframework.stereotype.Service;
import com.junzhou.exception.LoginFailedException;

import javax.servlet.http.HttpSession;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.Base64;
import javax.imageio.ImageIO;

@Service
public class LoginCaptchaService {
    private final SecureRandom random = new SecureRandom();

    public String create(HttpSession session) {
        synchronized (session) {
            String alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
            StringBuilder code = new StringBuilder();
            BufferedImage image = new BufferedImage(140, 48, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            try {
                g.setColor(new Color(250, 244, 244));
                g.fillRect(0, 0, 140, 48);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
                for (int i = 0; i < 4; i++) {
                    char c = alphabet.charAt(random.nextInt(alphabet.length()));
                    code.append(c);
                    g.setColor(new Color(130 + random.nextInt(70), 20 + random.nextInt(60), 40 + random.nextInt(60)));
                    g.drawString(String.valueOf(c), 9 + i * 32, 33 + random.nextInt(7));
                }
                for (int i = 0; i < 8; i++) {
                    g.setColor(new Color(190, 140, 150));
                    g.drawLine(random.nextInt(140), random.nextInt(48), random.nextInt(140), random.nextInt(48));
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(image, "png", out);
                session.setAttribute("login.captcha", code.toString());
                session.setAttribute("login.captcha.expires", System.currentTimeMillis() + 120000L);
                return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
            } catch (java.io.IOException e) {
                throw new IllegalStateException("验证码生成失败", e);
            } finally {
                g.dispose();
            }
        }
    }

    public void verify(HttpSession session, String answer) {
        if (session == null) throw new LoginFailedException("请刷新图片验证码");
        synchronized (session) {
            String expected = (String) session.getAttribute("login.captcha");
            Long expires = (Long) session.getAttribute("login.captcha.expires");
            session.removeAttribute("login.captcha");
            session.removeAttribute("login.captcha.expires");
            if (expected == null || expires == null || expires < System.currentTimeMillis() || answer == null || !expected.equalsIgnoreCase(answer.trim()))
                throw new LoginFailedException("验证码错误或已过期，请重新输入");
        }
    }
}

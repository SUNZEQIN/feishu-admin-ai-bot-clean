package com.sunzeqin.feishuadmin.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Map;

/**
 * 二维码生成服务。
 *
 * <p>作用：把授权链接生成 PNG 图片字节，后续交给飞书图片接口发送。</p>
 *
 * @author sunzeqin
 */
@Service
public class QrCodeService {
    // 整张授权图宽度。
    private static final int IMAGE_WIDTH = 760;

    // 整张授权图高度。
    private static final int IMAGE_HEIGHT = 900;

    // 二维码区域大小。
    private static final int QR_SIZE = 520;

    // 背景色。
    private static final Color BACKGROUND_COLOR = new Color(244, 248, 255);

    // 卡片背景色。
    private static final Color CARD_COLOR = Color.WHITE;

    // 主色。
    private static final Color PRIMARY_COLOR = new Color(31, 94, 255);

    // 主文本色。
    private static final Color TEXT_COLOR = new Color(31, 41, 55);

    // 次级文本色。
    private static final Color MUTED_TEXT_COLOR = new Color(107, 114, 128);

    /**
     * 生成二维码 PNG。
     *
     * @param text 二维码内容
     * @return PNG 字节数组
     */
    public byte[] generatePng(String text) {
        try {
            // 二维码内容不能为空。
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("二维码内容不能为空");
            }

            // 设置二维码参数，UTF-8 避免中文或特殊字符出问题。
            Map<EncodeHintType, Object> hints = Map.of(
                    EncodeHintType.CHARACTER_SET, "UTF-8",
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN, 2
            );

            // 生成二维码矩阵。
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(text, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE, hints);

            // 生成美化后的授权图片。
            BufferedImage image = buildAuthorizationImage(matrix);

            // 写成 PNG 字节。
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", outputStream);

            // 返回 PNG 字节。
            return outputStream.toByteArray();
        } catch (Exception e) {
            // 统一抛出业务可读异常。
            throw new IllegalStateException("生成授权二维码失败：" + e.getMessage(), e);
        }
    }

    private BufferedImage buildAuthorizationImage(BitMatrix matrix) {
        // 创建整张授权图片。
        BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);

        // 获取画笔。
        Graphics2D graphics = image.createGraphics();

        try {
            // 开启抗锯齿，让文字和圆角更柔和。
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // 绘制整体背景。
            graphics.setColor(BACKGROUND_COLOR);
            graphics.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);

            // 绘制白色卡片。
            int cardX = 70;
            int cardY = 60;
            int cardWidth = IMAGE_WIDTH - cardX * 2;
            int cardHeight = IMAGE_HEIGHT - cardY * 2;
            graphics.setColor(CARD_COLOR);
            graphics.fillRoundRect(cardX, cardY, cardWidth, cardHeight, 36, 36);

            // 绘制卡片边框。
            graphics.setColor(new Color(221, 231, 255));
            graphics.setStroke(new BasicStroke(2));
            graphics.drawRoundRect(cardX, cardY, cardWidth, cardHeight, 36, 36);

            // 绘制标题。
            graphics.setFont(new Font("Microsoft YaHei", Font.BOLD, 42));
            graphics.setColor(PRIMARY_COLOR);
            drawCenteredText(graphics, "飞书授权", 146);

            // 绘制说明。
            graphics.setFont(new Font("Microsoft YaHei", Font.PLAIN, 24));
            graphics.setColor(MUTED_TEXT_COLOR);
            drawCenteredText(graphics, "请使用飞书扫码完成身份授权", 198);

            // 绘制二维码白底。
            int qrX = (IMAGE_WIDTH - QR_SIZE) / 2;
            int qrY = 250;
            graphics.setColor(Color.WHITE);
            graphics.fillRoundRect(qrX - 24, qrY - 24, QR_SIZE + 48, QR_SIZE + 48, 28, 28);

            // 绘制二维码。
            drawQrCode(graphics, matrix, qrX, qrY);

            // 绘制底部提示。
            graphics.setFont(new Font("Microsoft YaHei", Font.PLAIN, 22));
            graphics.setColor(MUTED_TEXT_COLOR);
            drawCenteredText(graphics, "授权成功后，可继续以你的身份执行操作", 824);

            // 返回图片。
            return image;
        } finally {
            // 释放画笔。
            graphics.dispose();
        }
    }

    private void drawQrCode(Graphics2D graphics, BitMatrix matrix, int x, int y) {
        // 先绘制白底。
        graphics.setColor(Color.WHITE);
        graphics.fillRect(x, y, matrix.getWidth(), matrix.getHeight());

        // 再绘制黑色二维码点阵。
        graphics.setColor(new Color(17, 24, 39));
        for (int matrixX = 0; matrixX < matrix.getWidth(); matrixX++) {
            for (int matrixY = 0; matrixY < matrix.getHeight(); matrixY++) {
                if (matrix.get(matrixX, matrixY)) {
                    graphics.fillRect(x + matrixX, y + matrixY, 1, 1);
                }
            }
        }
    }

    private void drawCenteredText(Graphics2D graphics, String text, int y) {
        // 计算文字宽度。
        FontMetrics metrics = graphics.getFontMetrics();
        int textWidth = metrics.stringWidth(text);

        // 居中绘制文字。
        int x = (IMAGE_WIDTH - textWidth) / 2;
        graphics.drawString(text, x, y);
    }
}

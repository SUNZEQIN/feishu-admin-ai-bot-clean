package com.sunzeqin.feishuadmin.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import org.springframework.stereotype.Service;

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
            BitMatrix matrix = writer.encode(text, BarcodeFormat.QR_CODE, 480, 480, hints);

            // 写成 PNG 字节。
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", outputStream);

            // 返回 PNG 字节。
            return outputStream.toByteArray();
        } catch (Exception e) {
            // 统一抛出业务可读异常。
            throw new IllegalStateException("生成授权二维码失败：" + e.getMessage(), e);
        }
    }
}

package com.sunzeqin.feishuadmin.pojo.cli;

/**
 * CLI 命令执行结果。
 *
 * <p>作用：保存一次 lark-cli 命令的退出码、标准输出和错误输出。</p>
 *
 * @param command  命令文本
 * @param exitCode 退出码，0 表示成功
 * @param stdout   标准输出
 * @param stderr   错误输出
 *
 * @author sunzeqin
 */
public record CliCommandResult(String command, int exitCode, String stdout, String stderr) {
}

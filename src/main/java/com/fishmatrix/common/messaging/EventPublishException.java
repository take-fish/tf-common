package com.fishmatrix.common.messaging;

/**
 * 事件发布失败。消息队列不可用、序列化失败或 broker 拒绝时抛出，
 * 由调用方决定重试、补偿或降级，发布器本身不做隐式重试。
 */
public class EventPublishException extends RuntimeException {

    public EventPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}

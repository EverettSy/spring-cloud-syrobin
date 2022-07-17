package com.syrobin.cloud.webmvc.feign;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * @author syrobin
 * @version v1.0
 * @description:
 * @date 2022-07-13 21:51
 */
@NoArgsConstructor
@Data
public class HttpBinAnythingResponse {

    private Map<String, String> args;
    private String data;
    private Map<String, String> form;
    private Map<String, String> headers;
    private String method;
    private String origin;
    private String url;
}

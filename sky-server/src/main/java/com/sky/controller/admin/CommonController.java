package com.sky.controller.admin;

import com.sky.constant.MessageConstant;
import com.sky.result.Result;
import com.sky.utils.AliOssUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController    //是 @Component 的派生注解
@RequestMapping("/admin/common")
@Slf4j
@Api(tags = "通用接口")
public class CommonController {
    @Autowired
    private AliOssUtil aliOssUtil;//由OssConfiguration配置类的@Bean方法创建对象注册到容器中
    /**
     * 文件上传
     * @param file
     * @return
     */
    @ApiOperation("文件上传")
    @RequestMapping("/upload")
    public Result<String> upload(MultipartFile file){
        log.info("文件上传：{}",file);
        try {
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            String objectName = UUID.randomUUID().toString() + extension;
            String filePath = aliOssUtil.upload(file.getBytes(), objectName);
            return Result.success(filePath);
        } catch (IOException e) {
            log.error("文件上传失败：{}",e);
        }
        return Result.error(MessageConstant.UPLOAD_FAILED);
    }
}

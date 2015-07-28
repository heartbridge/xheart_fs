#xheart 文件服务器(基于netty框架)

### 安装

* `mvn clean package`

### 启动

* `java -jar xheart_fs-1.0.0-jar-with-dependencies.jar`

支持如下参数：

`--port`:文件服务器监听的端口，默认8585

`--baseDir`:文件存储的根目录，默认`/files/`

`--threshold`:文件压缩阀值(只对图片生效)，单位为：byte(字节)，默认为：1048576 bytes也即1M.
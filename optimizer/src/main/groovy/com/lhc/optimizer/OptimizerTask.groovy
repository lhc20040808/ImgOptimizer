package com.lhc.optimizer

import groovy.xml.Namespace
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

class OptimizerTask extends DefaultTask {
    @Input
    File manifestFile

    @Input
    File res

    @Input
    int apiLevel

    /**
     * 应用图标文件名
     */
    String launcher
    /**
     * 应用圆角图标文件名
     */
    String roundLauncher
    /**
     * webp工具
     */
    def webpTool
    /**
     * jpg压缩工具
     */
    def jpgTool
    /**
     * png压缩工具
     */
    def pngTool

    OptimizerTask() {
        group "optimizer"
        webpTool = OptimizerUtil.getTool(project, OptimizerUtil.WEBP_TOOL)
        jpgTool = OptimizerUtil.getTool(project, OptimizerUtil.JPG_TOOL)
        pngTool = OptimizerUtil.getTool(project, OptimizerUtil.PNG_TOOL)
    }

    @TaskAction
    def run() {
        project.logger.error "------------Parse AndroidManifest-----------"

        def ns = new Namespace('http://schemas.android.com/apk/res/android', 'android')
        Node xml = new XmlParser().parse(manifestFile)
        Node application = xml.application[0]
        def attrs = application.attributes()

        attrs.each {
            it ->
                println "attributeName:${it.key},value:${it.value}"
        }

        launcher = attrs[ns.icon]
        roundLauncher = attrs[ns.roundIcon]

        if (launcher) {
            launcher = subStrLauncher(launcher)
        }

        if (roundLauncher) {
            roundLauncher = subStrLauncher(roundLauncher)
        }

        project.logger.error "------------Collect Pic from res-----------"
        def pngs = []
        def jpgs = []

        res.eachDir {
            dir ->
                if (OptimizerUtil.isImgFolder(dir)) {
                    dir.eachFile {
                        file ->
                            if (OptimizerUtil.isPreOptimizeJpg(file) && isNonLauncher(file)) {
                                jpgs << file
                            } else if (OptimizerUtil.isPreOptimizePng(file) && isNonLauncher(file)) {
                                pngs << file
                            }
                    }
                }
        }

        project.logger.error "------------Optimize Pic-----------"
        def jpgsInvalid = []
        def pngsInvalid = []

        if (apiLevel >= 18) {
            project.logger.error "Api level greater than 18"
            pngs.each {
                convertWebp(it, pngsInvalid)
            }
            jpgs.each {
                convertWebp(it, jpgsInvalid)
            }
        } else if (apiLevel >= 14 && apiLevel < 18) {//Android 4.0 - 4.2.1之间只支持完全不透明的webp图
            project.logger.error "Api level greater than 14 and less than 18"
            def compress = []
            pngs.each {
                if (OptimizerUtil.isTransparent(it)) {
                    compress << it
                    project.logger.error "${it.name} has alpha channel,don't convert to webp"
                } else {
                    convertWebp(it, pngsInvalid)
                }
            }
            compressImg(pngTool, true, compress)
            jpgs.each {
                convertWebp(it, jpgsInvalid)
            }
        } else {
            project.logger.error "Api level less than 14"
            compressImg(pngTool, true, pngs)
            compressImg(jpgTool, false, jpgs)
        }
    }

    def subStrLauncher(String str) {
        str.substring(str.lastIndexOf("/") + 1, str.length())
    }

    def isNonLauncher(File file) {
        def name = file.name
        name != "${launcher}.png" && name != "${launcher}.jpg" && name != "${roundLauncher}.png" && name != "${roundLauncher}.jpg"
    }

    def convertWebp(File orgFile, def invalidCollections) {
        project.logger.error "convert ${orgFile.name} to webp"
        def name = orgFile.name
        name = name.subSequence(0, name.lastIndexOf("."))
        def dstFile = new File(orgFile.parent, "${name}.webp")
        def result = "${webpTool} -q 75 ${orgFile.absolutePath} -o ${dstFile.absolutePath}".execute()
        result.waitFor()

        if (result.exitValue() == 0) {
            //转换成功
            def orgSize = orgFile.length()
            def dstSize = dstFile.length()
            if (orgSize > dstSize) {
                //转换成功，删除原文件
                orgFile.delete()
            } else {
                //转换后文件体积变大，放弃转换，尝试压缩
                dstFile.delete()
                invalidCollections << orgFile
                project.logger.error "convert ${name} to webp bigger than origin"
            }
        } else {
            //转换失败
            invalidCollections << orgFile
            project.logger.error "convert ${name} to webp error"
        }
    }

    def compressImg(String tool, boolean isPng, def files) {
        files.each {
            File file ->
                def dstFile = new File(file.parent, "tmp-preOptimizer-${file.name}")
                def result
                if (isPng) {
                    result = "${tool} -brute -rem alla -reduce -q ${file.absolutePath} ${dstFile.absolutePath}".execute()
                } else {
                    result = "${tool} --quality 84 ${file.absolutePath} ${dstFile.absolutePath}".execute()
                }

                result.waitForProcessOutput()
                if (result.exitValue() == 0) {
                    def orgLength = file.length()
                    def dstLength = dstFile.length()

                    if (orgLength > dstLength) {
                        //压缩成功，删除原文件,将压缩文件重命名
                        file.delete()
                        dstFile.renameTo(file)
                    } else {
                        //压缩失败，文件体积变大，删除生成的文件
                        project.logger.error "compress ${file.name} bigger than origin file"
                        dstFile.delete()
                    }

                } else {
                    project.logger.error "compress ${file.name} error"
                }
        }
    }
}
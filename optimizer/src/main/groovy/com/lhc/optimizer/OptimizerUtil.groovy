package com.lhc.optimizer

import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.GradleException
import org.gradle.api.Project

import javax.imageio.ImageIO
import java.awt.image.BufferedImage

class OptimizerUtil {
    def static final DRAWABLE = "drawable"
    def static final MIPMAP = "mipmap"
    def static final PNG9 = ".9.png"
    def static final PNG = ".png"
    def static final JPG = ".jpg"
    def static final JPEG = ".jpeg"
    def static final PNG_TOOL = "pngcrush"
    def static final JPG_TOOL = "quetzli"
    def static final WEBP_TOOL = "cwebp"

    def static isImgFolder(File file) {
        return file.name.startsWith(DRAWABLE) || file.name.startsWith(MIPMAP)
    }

    def static isPreOptimizePng(File file) {
        return (file.name.endsWith(PNG) || file.name.endsWith(PNG.toUpperCase())) && !file.name.endsWith(PNG9) && !file.name.endsWith(PNG9.toUpperCase())
    }

    def static isPreOptimizeJpg(File file) {
        return file.name.endsWith(JPG) || file.name.endsWith(JPEG) || file.name.endsWith(JPG.toUpperCase()) || file.name.endsWith(JPEG.toUpperCase())
    }

    def static isTransparent(File file) {
        BufferedImage img = ImageIo.read(file)
        return img.colorModel.hasAlpha()
    }

    def static getTool(Project project, String fileName) {
        def toolName
        if (Os.isFamily(Os.FAMILY_WINDOWS)) {
            //windows系统
            toolName = "${fileName}_win.exe"
        } else if (Os.isFamily(Os.FAMILY_MAC)) {
            //Mac系统
            toolName = "${fileName}_darwin"
        } else {
            //默认其他为linux系统
            toolName = "${fileName}_linux"
        }

        def path = "${project.buildDir.absolutePath}/tools/${fileName}/${toolName}"
        def file = new File(path)

        if (!file.exists()) {
            file.parentFile.mkdirs()
            new FileOutputStream(file).withStream {
                def is = OptimizerUtil.class.getResourceAsStream("/${fileName}/${toolName}")
                it.write(is.getBytes())
            }
        }

        if (file.exists() && file.setExecutable(true)) {
            return file.absolutePath
        }

        throw GradleException("${toolName} doesn't exeist or couldn't execute")
    }
}
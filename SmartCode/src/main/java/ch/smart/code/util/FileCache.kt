package ch.smart.code.util

import android.os.Environment
import com.blankj.utilcode.util.ActivityUtils
import com.blankj.utilcode.util.EncryptUtils
import com.blankj.utilcode.util.FileUtils
import com.blankj.utilcode.util.Utils
import io.reactivex.Observable
import ch.smart.code.util.rx.SimpleObserver
import ch.smart.code.util.rx.toIoAndMain
import com.tencent.mmkv.MMKV
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

object FileCache {

    /**
     * 获取SD卡上的私有目录，这里的文件会随着App卸载而被删除
     * SD卡写权限：
     * API < 19：需要申请
     * API >= 19：不需要申请
     * 文件目录：
     * Context.getExternalFilesDir() 绝对路径：SDCard/Android/data/应用包名/files/
     * 缓存目录：
     * Context.getExternalCacheDir()  绝对路径：SDCard/Android/data/应用包名/cache/
     *
     * 获取SD卡上的公有目录，APP卸载不会删除文件，需要SD卡写权限
     * Environment.getExternalStoragePublicDirectory()
     */

    // MMKV的缓存目录
    private const val MMKV = "mmkv"

    // 图片文件缓存路径
    private const val IMAGE = "image"

    // 下载目录
    private const val DOWNLOAD = "download"

    // 临时缓存文件
    private const val TEMP = "temp"

    // 媒体文件缓存目录
    private const val MEDIA = "media"

    // WebView 缓存目录
    private const val WEB = "web"

    // 日志文件存储目录
    private const val LOG = "log"

    /**
     * 获取 cache 缓存目录
     * @param uniqueName 需要获取的目录名
     */
    fun getRootDir(uniqueName: String? = null): File? {
        try {
            val app = Utils.getApp()
            val file =
                if (Environment.MEDIA_MOUNTED == Environment.getExternalStorageState() || !Environment.isExternalStorageRemovable()) {
                    app.externalCacheDir ?: app.cacheDir
                } else {
                    app.cacheDir
                }
            if (uniqueName.isNullOrBlank()) return file
            val uniqueFile = File(file, uniqueName)
            val existsDir = FileUtils.createOrExistsDir(uniqueFile)
            return if (!existsDir) {
                Timber.e("创建%s文件夹失败", uniqueName)
                val uFile = File(Utils.getApp().cacheDir, uniqueName)
                val mkdirs = FileUtils.createOrExistsDir(uFile)
                if (mkdirs) uFile else null
            } else {
                uniqueFile
            }
        } catch (e: Exception) {
            Timber.e(e)
        }
        return null
    }

    fun getTempDir(): File? {
        return getRootDir(uniqueName = TEMP)
    }

    fun getLogDir(): File? {
        return getRootDir(uniqueName = LOG)
    }

    fun getDownloadDir(): File? {
        return getRootDir(uniqueName = DOWNLOAD)
    }

    fun getImageDir(): File? {
        return getRootDir(uniqueName = IMAGE)
    }

    fun getMediaDir(): File? {
        return getRootDir(uniqueName = MEDIA)
    }

    fun getWebDir(): File? {
        return getRootDir(uniqueName = WEB)
    }

    fun getMMKVDir(): File? {
        val file = File(Utils.getApp().filesDir, MMKV)
        file.mkdirs()
        return file
    }

    fun getSuffix(path: String, defSuffix: String? = null): String? {
        try {
            if (path.contains(".")) {
                val check = path.indexOfFirst {
                    it.toString() == "?"
                }
                val str = if (check >= 0) {
                    val checkPath = path.substring(0, check)
                    val index = checkPath.lastIndexOf(".") + 1
                    val check2 = checkPath.lastIndexOf("/")
                    if (check2 > index) {
                        getSuffix(path.substring(check + 1), defSuffix = defSuffix)
                    } else {
                        checkPath.substring(index)
                    }
                } else {
                    val index = path.lastIndexOf(".") + 1
                    val checkPath = path.substring(index)
                    val check2 = checkPath.indexOfFirst { it.toString() == "&" }
                    if (check2 > 0) {
                        checkPath.substring(0, check2)
                    } else {
                        checkPath
                    }
                }
                Timber.i("截取到后缀：%s \n %s", str, path)
                return str
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return defSuffix
    }

    fun getUrlFile(
        url: String,
        dir: File? = getDownloadDir(),
        tag: String? = null,
        fixSuffix: String? = null,
        defSuffix: String? = null
    ): File? {
        return if (dir?.exists() == true) File(
            dir, String.format(
                "%s_%s.%s",
                if (tag.isNotNullOrBlank()) tag else "",
                EncryptUtils.encryptMD5ToString(url).toUpperCase(),
                if (fixSuffix.isNullOrEmpty()) getSuffix(url, defSuffix = defSuffix) else fixSuffix
            )
        ) else null
    }

    @JvmOverloads
    fun clear(uniqueName: String? = null, endAction: (() -> Unit)? = null) {
        val file = getRootDir(uniqueName = uniqueName)
        if (file == null || !file.exists()) {
            endAction?.invoke()
            return
        }
        Observable.just(file).map {
            FileUtils.delete(file)
            true
        }.delay(1, TimeUnit.SECONDS).toIoAndMain()
            .doOnSubscribe {
                showLoading(ActivityUtils.getTopActivity(), cancelable = false)
            }
            .doFinally {
                dismissLoading()
            }
            .subscribe(object : SimpleObserver<Boolean>() {
                override fun onNext(t: Boolean) {
                    endAction?.invoke()
                }
            })
    }

    fun getStorageDir(type: String = Environment.DIRECTORY_DOWNLOADS): File? {
        try {
            if (Environment.MEDIA_MOUNTED == Environment.getExternalStorageState() || !Environment.isExternalStorageRemovable()) {
                val file = Environment.getExternalStoragePublicDirectory(type)
                if (FileUtils.createOrExistsDir(file)) {
                    return file
                } else {
                    Timber.e("文件夹不存在：%s", file?.absolutePath)
                }
            }
        } catch (e: Exception) {
            Timber.e(e)
        }
        return null
    }
}

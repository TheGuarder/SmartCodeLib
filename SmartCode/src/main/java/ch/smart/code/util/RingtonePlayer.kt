package ch.smart.code.util

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import ch.smart.code.imageloader.isStartsWithHttp
import com.blankj.utilcode.util.Utils
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import ch.smart.code.util.rx.SimpleObserver
import ch.smart.code.util.rx.toIoAndMain
import io.reactivex.rxkotlin.subscribeBy
import timber.log.Timber
import zlc.season.rxdownload4.download
import zlc.season.rxdownload4.task.Task
import java.io.File

class RingtonePlayer(private val repeat: Boolean = true) {

    private var isLoadingFile = false
    private var disposable: Disposable? = null
    private var mediaPlayer: MediaPlayer? = null

    fun isLoading(): Boolean {
        return isLoadingFile || mediaPlayer?.isLooping == true
    }

    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying == true
    }

    fun start(url: String?) {
        if (isLoading() || isPlaying()) {
            return
        }
        if (url?.isStartsWithHttp() != true) {
            actionError(Exception("无效url"))
            return
        }
        isLoadingFile = true
        val file = FileCache.getUrlFile(url, dir = FileCache.getMediaDir(), defSuffix = "mp3")
        if (file == null) {
            actionError(Exception("File获取失败"))
            isLoadingFile = false
            return
        }
        if (file.exists()) {
            startPlay(Uri.fromFile(file))
            return
        }
        if (!FileCache.getMediaDir().isAvailableSpace(50)) {
            actionError(Exception("cacheDir可用空间不足50M"))
            isLoadingFile = false
            return
        }
        startLoad(url, file)
    }

    fun start(type: Int = RingtoneManager.TYPE_RINGTONE) {
        if (isLoading() || isPlaying()) {
            return
        }
        startPlay(RingtoneManager.getDefaultUri(type))
    }

    private fun startLoad(url: String, file: File) {
        stopLoad()
        disposable = Task(
            url,
            taskName = file.name,
            saveName = file.name,
            savePath = file.parent ?: ""
        ).download().toIoAndMain().subscribeBy(
            onNext = {
                Timber.i("下载%s：%s", it.percent(), url)
            },
            onComplete = {
                startPlay(Uri.fromFile(file))
                stopLoad()
            },
            onError = {
                actionError(it)
                isLoadingFile = false
                stopLoad()
            }
        )
    }

    private fun stopLoad() {
        disposable?.dispose()
        disposable = null
    }

    private fun startPlay(uri: Uri) {
        isLoadingFile = false
        Observable.just(uri)
            .map {
                MediaPlayer.create(Utils.getApp(), it).apply {
                    try {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                    } catch (e: Exception) {
                        Timber.e(e)
                    }
                    isLooping = repeat
                }
            }
            .subscribe(object : SimpleObserver<MediaPlayer>() {
                override fun onNext(t: MediaPlayer) {
                    stopPlay()
                    mediaPlayer = t.apply { start() }
                }
            })
    }

    private fun stopPlay() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    private fun actionError(e: Throwable?) {
        Timber.e(e)
    }

    fun stop() {
        isLoadingFile = false
        stopLoad()
        stopPlay()
    }
}

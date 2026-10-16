package moe.chensi.volume

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import org.joor.Reflect

/** Builds render mixes compatible with AudioPolicy UID-device affinities. */
internal object AudioOutputMixFactory {
    private const val ROUTE_FLAG_RENDER = 1
    private const val RULE_MATCH_ATTRIBUTE_USAGE = 1

    // A mix containing MATCH_UID for a routed UID conflicts with setUidDeviceAffinity.
    // Match the SDK usages instead; affinities then exclude that UID from non-target mixes.
    private val routableUsages = intArrayOf(
        AudioAttributes.USAGE_UNKNOWN,
        AudioAttributes.USAGE_MEDIA,
        AudioAttributes.USAGE_VOICE_COMMUNICATION,
        AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING,
        AudioAttributes.USAGE_ALARM,
        AudioAttributes.USAGE_NOTIFICATION,
        AudioAttributes.USAGE_NOTIFICATION_RINGTONE,
        AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_REQUEST,
        AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT,
        AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_DELAYED,
        AudioAttributes.USAGE_NOTIFICATION_EVENT,
        AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY,
        AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
        AudioAttributes.USAGE_ASSISTANCE_SONIFICATION,
        AudioAttributes.USAGE_GAME,
        AudioAttributes.USAGE_ASSISTANT
    )

    fun build(device: AudioDeviceInfo): Any {
        val ruleBuilder = Reflect.onClass("android.media.audiopolicy.AudioMixingRule\$Builder")
            .create()
        routableUsages.forEach { usage ->
            val attributes = AudioAttributes.Builder().setUsage(usage).build()
            ruleBuilder.call("addRule", attributes, RULE_MATCH_ATTRIBUTE_USAGE)
        }
        val rule = ruleBuilder.call("build").get<Any>()

        val mixBuilder = Reflect.onClass("android.media.audiopolicy.AudioMix\$Builder").create()
        mixBuilder.call("setMixingRule", rule)
        mixBuilder.call("setRouteFlags", ROUTE_FLAG_RENDER)
        mixBuilder.call("setDevice", device)
        return mixBuilder.call("build").get<Any>()
    }
}

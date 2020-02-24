package com.fpd.teamcity.slack

import com.fpd.teamcity.slack.ConfigManager.BuildSettingFlag
import com.fpd.teamcity.slack.ConfigManager.BuildSettingFlag.BuildSettingFlag
import com.fpd.teamcity.slack.Helpers.Implicits._
import jetbrains.buildServer.messages.Status
import jetbrains.buildServer.serverSide.{BuildServerAdapter, SBuildServer, SRunningBuild}

class SlackServerAdapter(sBuildServer: SBuildServer,
                         val configManager: ConfigManager,
                         val gateway: SlackGateway,
                         val messageBuilderFactory: MessageBuilderFactory
                        ) extends BuildServerAdapter with NotificationSender {

  import SlackServerAdapter._

  sBuildServer.addListener(this)

  override def buildFinished(build: SRunningBuild): Unit = if (configManager.isAvailable) {
    val previousStatus = sBuildServer.findPreviousStatus(build)

    val flags = calculateFlags(previousStatus, build.getBuildStatus)
    //val failedBuildFlag = configManager.buildSetting() && !build.getFailureReasons.stream().anyMatch(msg => msg.getDescription.contains("Process exited with code"))
    if (flags.nonEmpty /*&& failedBuildFlag*/) {
      send(build, flags)
    }
  }

  override def buildStarted(build: SRunningBuild): Unit = if (configManager.isAvailable)
    send(build, Set(BuildSettingFlag.started))

  override def buildInterrupted(build: SRunningBuild): Unit = if (configManager.isAvailable)
    send(build, Set(BuildSettingFlag.canceled))
}

object SlackServerAdapter {
  def calculateFlags(previous: Status, current: Status): Set[BuildSettingFlag] = {
    import BuildSettingFlag._

    def changed = statusChanged(previous, current)

    def applyIfChanged(flag1: BuildSettingFlag, flag2: BuildSettingFlag) = if (changed) Set(flag1, flag2) else Set(flag1)

    if (current.isSuccessful) {
      applyIfChanged(success, failureToSuccess)
    } else if (current.isFailed) {
      applyIfChanged(failure, successToFailure)
    } else {
      Set()
    }
  }

  def statusChanged(previous: Status, current: Status): Boolean = {
    import Helpers.Implicits._

    if (previous.isSuccessful) {
      current.isFailed
    } else if (previous.isFailed) {
      current.isSuccessful
    } else if (previous.isUnknown) {
      false
    } else {
      current.isSuccessful || (!previous.isUnknown && current.isUnknown)
    }
  }
}

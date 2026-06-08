package com.o2.commit.util

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

/**
 * 通知封装,统一使用 plugin.xml 声明的 "o2commit" 通知组。
 *
 * @author will
 * @since 2026-06-07
 */
object O2Notifier {

    private fun notify(project: Project, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("o2commit")
            .createNotification(content, type)
            .notify(project)
    }

    fun info(project: Project, content: String) = notify(project, content, NotificationType.INFORMATION)

    fun warn(project: Project, content: String) = notify(project, content, NotificationType.WARNING)

    fun error(project: Project, content: String) = notify(project, content, NotificationType.ERROR)
}

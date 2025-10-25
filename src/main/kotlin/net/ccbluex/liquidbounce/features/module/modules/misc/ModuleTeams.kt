/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2025 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.features.module.modules.misc

import net.ccbluex.liquidbounce.config.types.NamedChoice
import net.ccbluex.liquidbounce.event.events.TagEntityEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.client.stripMinecraftColorCodes
import net.ccbluex.liquidbounce.utils.inventory.getArmorColor
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import java.awt.Color

/**
 * Teams module
 *
 * Prevents KillAura from attacking teammates and applies team/armor color tags.
 */
object ModuleTeams : ClientModule("Teams", Category.MISC) {
    private val matches by multiEnumChoice("Matches",
        Matches.SCOREBOARD_TEAM,
        Matches.NAME_COLOR
    )

    private val armorColor by multiEnumChoice("ArmorColor",
        ArmorColor.HELMET
    )

    @Suppress("unused")
    val entityTagEvent = handler<TagEntityEvent> { event ->
        val entity = event.entity

        val (color, isTeammate) = getEntityColorAndRelation(entity)

        if (color != null) {
            event.color(color, Priority.IMPORTANT_FOR_USAGE_1)
        }

        if (isTeammate) {
            event.dontTarget()
        }
    }

    /**
     * Returns a pair (Color4b?, isTeammate)
     * - Uses team color if available.
     * - Falls back to armor color (or name color) if team color not visible.
     */
    private fun getEntityColorAndRelation(entity: Entity): Pair<Color4b?, Boolean> {
        if (entity !is LivingEntity) {
            return null to false
        }

        val isTeammate =
            matches.any { it.testMatches(entity) } ||
                (entity is PlayerEntity && armorColor.any { it.matchesArmorColor(entity) })

        val teamColor = entity.displayName?.style?.color?.rgb?.let { Color4b(Color(it)) }

        val fallbackColor =
            when (entity) {
                is PlayerEntity -> armorColor.firstNotNullOfOrNull { part ->
                    entity.inventory.getArmorStack(part.slot).getArmorColor()
                        ?.let { Color4b(Color(it)) }
                }
                else -> null
            } ?: teamColor

        return fallbackColor to isTeammate
    }

    @Suppress("unused")
    private enum class Matches(
        override val choiceName: String,
        val testMatches: (suspected: LivingEntity) -> Boolean
    ) : NamedChoice {
        SCOREBOARD_TEAM("ScoreboardTeam", { suspected ->
            player.isTeammate(suspected)
        }),

        NAME_COLOR("NameColor", { suspected ->
            val targetColor = player.displayName?.style?.color
            val clientColor = suspected.displayName?.style?.color

            targetColor != null && clientColor != null && targetColor == clientColor
        }),

        PREFIX("Prefix", { suspected ->
            val targetSplit = suspected.displayName
                ?.string
                ?.stripMinecraftColorCodes()
                ?.split(" ")

            val clientSplit = player.displayName
                ?.string
                ?.stripMinecraftColorCodes()
                ?.split(" ")

            targetSplit != null &&
                clientSplit != null &&
                targetSplit.size > 1 &&
                clientSplit.size > 1 &&
                targetSplit[0] == clientSplit[0]
        })
    }

    @Suppress("unused", "MagicNumber")
    private enum class ArmorColor(
        override val choiceName: String,
        val slot: Int
    ) : NamedChoice {
        HELMET("Helmet", 3),
        CHESTPLATE("Chestplate", 2),
        PANTS("Pants", 1),
        BOOTS("Boots", 0);

        fun matchesArmorColor(suspected: PlayerEntity): Boolean {
            val ownColor = player.inventory.getArmorStack(slot).getArmorColor() ?: return false
            val otherColor = suspected.inventory.getArmorStack(slot).getArmorColor() ?: return false
            return ownColor == otherColor
        }
    }
}

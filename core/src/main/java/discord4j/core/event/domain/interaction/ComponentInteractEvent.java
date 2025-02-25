/*
 * This file is part of Discord4J.
 *
 * Discord4J is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Discord4J is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Discord4J. If not, see <http://www.gnu.org/licenses/>.
 */
package discord4j.core.event.domain.interaction;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.command.ApplicationCommandInteraction;
import discord4j.core.object.command.Interaction;
import discord4j.core.object.component.Button;
import discord4j.core.object.component.MessageComponent;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.InteractionApplicationCommandCallbackEditMono;
import discord4j.core.spec.InteractionApplicationCommandCallbackSpec;
import discord4j.core.spec.legacy.LegacyInteractionApplicationCommandCallbackSpec;
import discord4j.discordjson.json.InteractionApplicationCommandCallbackData;
import discord4j.gateway.ShardInfo;
import discord4j.rest.util.InteractionResponseType;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Dispatched when a user interacts with a {@link MessageComponent} the bot has sent.
 * <p>
 * This is not directly dispatched by Discord, but is a utility specialization of {@link InteractionCreateEvent}.
 */
public class ComponentInteractEvent extends InteractionCreateEvent {

    public ComponentInteractEvent(GatewayDiscordClient gateway, ShardInfo shardInfo, Interaction interaction) {
        super(gateway, shardInfo, interaction);
    }

    /**
     * Gets the developer-defined custom id associated with the component.
     *
     * @return The component's custom id.
     * @see Button#getCustomId()
     */
    public String getCustomId() {
        return getInteraction().getCommandInteraction()
                .flatMap(ApplicationCommandInteraction::getCustomId)
                // note: custom_id is not guaranteed to present on components in general (e.g., link buttons),
                // but it is guaranteed to be present here, because we received an interaction_create for it
                // (which doesn't happen for components without custom_id)
                .orElseThrow(IllegalStateException::new);
    }

    /**
     * Gets the message the component is on.
     * <p>
     * For ephemeral messages, only the ID is present. Use {@link #getMessageId()}
     *
     * @return The message the component is on.
     */
    public Optional<Message> getMessage() {
        return getInteraction().getMessage();
    }

    /**
     * Gets the ID of the message the component is on.
     *
     * @return The ID of the message the component is on.
     */
    public Snowflake getMessageId() {
        return getInteraction().getMessageId()
                .orElseThrow(IllegalStateException::new); // at least the ID is always present for component interactions
    }

    /**
     * Requests to respond to the interaction by immediately editing the message the button is on.
     *
     * @param spec A {@link Consumer} that provides a "blank" {@link InteractionApplicationCommandCallbackSpec} to be
     * operated on.
     * @return A {@link Mono} where, upon successful completion, emits nothing; indicating the interaction response has
     * been sent. If an error is received, it is emitted through the {@code Mono}.
     * @deprecated use {@link #edit()}, {@link #edit(InteractionApplicationCommandCallbackSpec)}  which offer an
     * immutable approach to build specs
     */
    @Deprecated
    public Mono<Void> edit(Consumer<? super LegacyInteractionApplicationCommandCallbackSpec> spec) {
        return Mono.defer(
                () -> {
                    LegacyInteractionApplicationCommandCallbackSpec mutatedSpec =
                            new LegacyInteractionApplicationCommandCallbackSpec();

                    getClient().getRestClient().getRestResources()
                            .getAllowedMentions()
                            .ifPresent(mutatedSpec::setAllowedMentions);

                    spec.accept(mutatedSpec);

                    return createInteractionResponse(InteractionResponseType.UPDATE_MESSAGE, mutatedSpec.asRequest());
                });
    }

    /**
     * Requests to respond to the interaction by immediately editing the message the button is on. Properties specifying
     * how to edit the message can be set via the {@code withXxx} methods of the returned
     * {@link InteractionApplicationCommandCallbackEditMono}.
     *
     * @return A {@link InteractionApplicationCommandCallbackEditMono} where, upon successful completion, emits nothing;
     * indicating the interaction response has been sent. If an error is received, it is emitted through the {@code
     * InteractionApplicationCommandCallbackMono}.
     */
    public InteractionApplicationCommandCallbackEditMono edit() {
        return InteractionApplicationCommandCallbackEditMono.of(this);
    }

    /**
     * Requests to respond to the interaction by immediately editing the message the button is on.
     *
     * @param spec an immutable object that specifies how to edit the message the button is on.
     * @return A {@link Mono} where, upon successful completion, emits nothing; indicating the interaction response has
     * been sent. If an error is received, it is emitted through the {@code Mono}.
     */
    public Mono<Void> edit(InteractionApplicationCommandCallbackSpec spec) {
        Objects.requireNonNull(spec);
        return Mono.defer(
                () -> {
                    InteractionApplicationCommandCallbackSpec actualSpec = getClient().getRestClient()
                            .getRestResources()
                            .getAllowedMentions()
                            .map(spec::withAllowedMentions)
                            .orElse(spec);

                    return createInteractionResponse(InteractionResponseType.UPDATE_MESSAGE, actualSpec.asRequest());
                });
    }

    @Override
    public Mono<Void> acknowledge() {
        return createInteractionResponse(InteractionResponseType.DEFERRED_UPDATE_MESSAGE, null);
    }

    @Override
    public Mono<Void> acknowledgeEphemeral() {
        InteractionApplicationCommandCallbackData data = InteractionApplicationCommandCallbackData.builder()
                .flags(Message.Flag.EPHEMERAL.getFlag())
                .build();

        return createInteractionResponse(InteractionResponseType.DEFERRED_UPDATE_MESSAGE, data);
    }
}

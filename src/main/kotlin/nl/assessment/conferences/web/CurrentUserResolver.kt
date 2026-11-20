package nl.assessment.conferences.web

import nl.assessment.conferences.db.UserDirectory
import nl.assessment.conferences.domain.User
import org.springframework.context.annotation.Configuration
import org.springframework.core.MethodParameter
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.util.UUID

/** Resolves the `User` parameter of a controller method from the `X-User-Id` header. No real login. */
@Configuration
class CurrentUserResolver(private val users: UserDirectory) : HandlerMethodArgumentResolver, WebMvcConfigurer {

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(this)
    }

    override fun supportsParameter(parameter: MethodParameter) = parameter.parameterType == User::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): User {
        val id = webRequest.getHeader("X-User-Id")?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        return id?.let(users::find) ?: throw Unauthenticated()
    }
}

package moe.chensi.volume.system

import android.content.Context
import android.os.UserManager
import moe.chensi.volume.EnableBinderProxy
import moe.chensi.volume.ToggleableBinderProxy
import org.joor.Reflect

class UserManagerProxy(context: Context) {
    private val userManager = Reflect.on(context.getSystemService(UserManager::class.java)!!)

    init {
        // Can't use `userManager.field("mService")` as it will have type of `IUserManager`
        // instead of concrete type `IUserManager.Stub.Proxy`
        val mService = userManager.get<Any>("mService")
        ToggleableBinderProxy.wrap(mService)
    }

    @EnableBinderProxy
    fun getUserIds(): List<Int> {
        return userManager.call("getUsers").get<List<Any>>()
            .map { value -> Reflect.on(value).get("id") }
    }
}

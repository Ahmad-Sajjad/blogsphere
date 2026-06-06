package com.example.blogsphere.ui.groups

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.blogsphere.R
import com.example.blogsphere.data.AuthRepository
import com.example.blogsphere.data.DataStore
import com.example.blogsphere.data.Group
import com.example.blogsphere.util.Broadcasts
import com.example.blogsphere.util.toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class GroupsFragment : Fragment() {

    private lateinit var adapter: GroupsAdapter

    private val reloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { reload() }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_groups, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rv = view.findViewById<RecyclerView>(R.id.rvGroups)
        val fab = view.findViewById<FloatingActionButton>(R.id.fabCreateGroup)
        val join = view.findViewById<MaterialButton>(R.id.btnJoin)

        adapter = GroupsAdapter(mutableListOf()) { g ->
            startActivity(Intent(requireContext(), GroupDetailActivity::class.java)
                .putExtra(GroupDetailActivity.EXTRA_GROUP_ID, g.id))
            requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        fab.setOnClickListener { createGroupDialog() }
        join.setOnClickListener { joinGroupDialog() }

        reload()
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(reloadReceiver, IntentFilter(Broadcasts.ACTION_GROUPS_CHANGED))
        reload()
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(reloadReceiver)
    }

    private fun reload() {
        adapter.replace(DataStore.groups.toList())
    }

    private fun createGroupDialog() {
        val user = DataStore.currentUser ?: return
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_create_group, null)
        val tilName = view.findViewById<TextInputLayout>(R.id.tilGroupName)
        val etName = view.findViewById<TextInputEditText>(R.id.etGroupName)
        val etDesc = view.findViewById<TextInputEditText>(R.id.etGroupDesc)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Create a group")
            .setView(view)
            .setPositiveButton("Create", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = etName.text?.toString()?.trim().orEmpty()
                val desc = etDesc.text?.toString()?.trim().orEmpty()
                tilName.error = null
                if (name.length < 3) {
                    tilName.error = "Name must be at least 3 characters"
                    return@setOnClickListener
                }
                val g = Group(
                    id = DataStore.newId(),
                    name = name,
                    description = desc.ifBlank { "No description." },
                    inviteCode = DataStore.generateInviteCode(),
                    creatorId = user.id,
                    memberIds = mutableListOf(user.id)
                )
                DataStore.addGroup(g)
                user.joinedGroupIds.add(g.id)
                AuthRepository.saveCurrentUserAsync()   // persist joinedGroupIds
                toast("Group created! Code: ${g.inviteCode}")
                LocalBroadcastManager.getInstance(requireContext())
                    .sendBroadcast(Intent(Broadcasts.ACTION_GROUPS_CHANGED))
                reload()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun joinGroupDialog() {
        val user = DataStore.currentUser ?: return
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_join_group, null)
        val tilCode = view.findViewById<TextInputLayout>(R.id.tilInviteCode)
        val etCode = view.findViewById<TextInputEditText>(R.id.etInviteCode)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Join a group")
            .setMessage("Enter the 6-character invite code.")
            .setView(view)
            .setPositiveButton("Join", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val code = etCode.text?.toString()?.trim().orEmpty()
                tilCode.error = null
                if (code.isEmpty()) {
                    tilCode.error = "Code required"
                    return@setOnClickListener
                }
                val g = DataStore.findGroupByCode(code)
                if (g == null) {
                    tilCode.error = "No group with that code"
                    return@setOnClickListener
                }
                if (g.memberIds.contains(user.id)) {
                    toast("You're already a member")
                } else {
                    DataStore.joinGroup(user, g)
                    AuthRepository.saveCurrentUserAsync()   // persist joinedGroupIds
                    toast("Joined ${g.name}!")
                    LocalBroadcastManager.getInstance(requireContext())
                        .sendBroadcast(Intent(Broadcasts.ACTION_GROUPS_CHANGED))
                    reload()
                }
                dialog.dismiss()
            }
        }
        dialog.show()
    }
}

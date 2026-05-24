package com.mobicloud.presentation.join

import androidx.lifecycle.ViewModel
import com.mobicloud.domain.models.m11_join.NodeJoinState
import com.mobicloud.domain.usecase.m11_join.JoinStateMachine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class JoinOverlayViewModel @Inject constructor(
    joinStateMachine: JoinStateMachine
) : ViewModel() {
    val joinState: StateFlow<NodeJoinState> = joinStateMachine.currentState
}

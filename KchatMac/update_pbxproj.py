#!/usr/bin/env python3

import sys
import uuid

PBX_FILE_REF_TEMPLATE = '		{uuid} /* {name} */ = {{isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = {path}; sourceTree = "<group>"; }};'

PBX_BUILD_FILE_TEMPLATE = '		{uuid} /* {name} in Sources */ = {{isa = PBXBuildFile; fileRef = {file_ref_uuid} /* {name} */; }};'

def generate_uuid():
    return str(uuid.uuid4()).upper().replace('-', '')[:24]

def main():
    files = [
        ('APIClient.swift', 'Services/APIClient.swift'),
        ('SSEParser.swift', 'Services/SSEParser.swift'),
        ('ChatService.swift', 'Services/ChatService.swift'),
        ('TokenService.swift', 'Services/TokenService.swift'),
        ('ThemeService.swift', 'Services/ThemeService.swift'),
        ('KChatContainer.swift', 'Data/KChatContainer.swift'),
        ('Conversation.swift', 'Data/Chat/Conversation.swift'),
        ('Message.swift', 'Data/Chat/Message.swift'),
        ('ModelConfig.swift', 'Data/Model/ModelConfig.swift'),
        ('Note.swift', 'Data/NoteTodo/Note.swift'),
        ('Todo.swift', 'Data/NoteTodo/Todo.swift'),
        ('ChatViewModel.swift', 'ViewModels/ChatViewModel.swift'),
        ('SidebarViewModel.swift', 'ViewModels/SidebarViewModel.swift'),
        ('SettingsViewModel.swift', 'ViewModels/SettingsViewModel.swift'),
        ('NoteTodoViewModel.swift', 'ViewModels/NoteTodoViewModel.swift'),
        ('ChatView.swift', 'Views/Chat/ChatView.swift'),
        ('MessageBubble.swift', 'Views/Chat/MessageBubble.swift'),
        ('InputArea.swift', 'Views/Chat/InputArea.swift'),
        ('MarkdownView.swift', 'Views/Chat/MarkdownView.swift'),
        ('TypingIndicator.swift', 'Views/Chat/TypingIndicator.swift'),
        ('SidebarView.swift', 'Views/Sidebar/SidebarView.swift'),
        ('ConversationItem.swift', 'Views/Sidebar/ConversationItem.swift'),
        ('SettingsView.swift', 'Views/Settings/SettingsView.swift'),
        ('NoteTodoView.swift', 'Views/NoteTodo/NoteTodoView.swift'),
        ('ToastView.swift', 'Views/Common/ToastView.swift'),
        ('ErrorCard.swift', 'Views/Common/ErrorCard.swift'),
        ('EmptyView.swift', 'Views/Common/EmptyView.swift'),
    ]

    file_refs = []
    build_files = []
    file_ref_map = {}

    for name, path in files:
        file_ref_uuid = generate_uuid()
        build_file_uuid = generate_uuid()
        
        file_ref_map[name] = file_ref_uuid
        
        file_refs.append(PBX_FILE_REF_TEMPLATE.format(
            uuid=file_ref_uuid,
            name=name,
            path=path
        ))
        
        build_files.append(PBX_BUILD_FILE_TEMPLATE.format(
            uuid=build_file_uuid,
            name=name,
            file_ref_uuid=file_ref_uuid
        ))

    print("\n".join(file_refs))
    print("\n")
    print("\n".join(build_files))
    print("\n")
    
    print("Source file references for PBXSourcesBuildPhase:")
    for name, path in files:
        build_uuid = generate_uuid()
        file_ref_uuid = file_ref_map[name]
        print(f'\t\t{build_uuid} /* {name} in Sources */ = {{isa = PBXBuildFile; fileRef = {file_ref_uuid} /* {name} */; }};')

if __name__ == '__main__':
    main()
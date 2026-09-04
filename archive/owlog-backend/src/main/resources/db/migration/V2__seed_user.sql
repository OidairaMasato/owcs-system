insert into users (battle_tag, platform, display_name)
values ('おれまさと#3428', 'console', 'おれまさと')
on conflict (battle_tag) do nothing;

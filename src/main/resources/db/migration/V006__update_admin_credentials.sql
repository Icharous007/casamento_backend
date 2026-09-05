-- Atualiza as credenciais do administrador padrão em bancos já inicializados.
UPDATE internal_users
SET email = 'gustavopsousa@gmail.com',
    password_hash = '$2a$12$lB3jV55oQCh2kVl5/X0GW.GDJvNE50YL7iQ.VoxOIRDCw9lspn09u'
WHERE email = 'admin@casamento.local';
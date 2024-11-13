To connect to LSP from neovim:

1. Use UNIX(-like) system
2. Install neovim https://neovim.io/ and netcat (usually known as `nc`)
3. Add the following to the bottom of the `~/.config/nvim/init.lua` file:

```lua
local port = "8979"

local client = vim.lsp.start_client({
    name = "dbeaver.nvim",
    cmd = { "nc", "127.0.0.1", port },
})

if not client then
    vim.notify("error instantiating dbeaver.nvim client")
    return
end

vim.api.nvim_create_autocmd("FileType", {
    pattern = "sql",
    callback = function()
        vim.lsp.buf_attach_client(0, client)
    end,
})
```

4. Start the server.
5. Execute `nvim /path/to/some/sql/file`

To check LSP status, use the `:checkhealh lsp` command.
